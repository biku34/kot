package com.procmon.app.server

import android.content.Context
import com.procmon.app.data.AppCollector
import com.procmon.app.util.UsageAccess
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * Embedded Ktor (CIO) server. Serves:
 *  - the exported Next.js PWA from assets/web  (GET /, /_next/*, …)
 *  - the JSON API                              (GET /api/apps, GET /api/stream, …)
 *
 * Because the PWA and the API share this origin, the browser makes same-origin
 * requests to http://localhost:<port> with no CORS friction.
 */
class WebServer(
    private val context: Context,
    private val port: Int = DEFAULT_PORT,
) {
    private val collector = AppCollector(context.applicationContext)
    private val json = Json { encodeDefaults = true; explicitNulls = true }
    private var engine: ApplicationEngine? = null

    fun start() {
        if (engine != null) return
        engine = embeddedServer(CIO, port = port, host = "127.0.0.1") {
            install(ContentNegotiation) { json(json) }
            install(CORS) {
                anyHost() // harmless: bound to loopback only; helps `next dev` from a laptop.
                allowHeader(HttpHeaders.ContentType)
            }
            routing {
                get("/health") { call.respond(mapOf("ok" to true)) }

                get("/api/apps") {
                    call.response.header(HttpHeaders.CacheControl, CacheControl.NoStore(null).toString())
                    call.respond(collector.snapshot())
                }

                post("/api/permissions/usage-access") {
                    UsageAccess.openSettings(context)
                    call.respond(HttpStatusCode.OK, mapOf("opened" to true))
                }

                // Server-sent events: push a fresh snapshot every INTERVAL_MS.
                get("/api/stream") {
                    call.response.header(HttpHeaders.CacheControl, "no-cache")
                    call.response.header("Connection", "keep-alive")
                    call.respondTextWriter(contentType = ContentType.parse("text/event-stream")) {
                        try {
                            while (coroutineContext.isActive) {
                                val payload = json.encodeToString(
                                    com.procmon.app.data.Snapshot.serializer(),
                                    collector.snapshot(),
                                )
                                write("event: snapshot\n")
                                write("data: $payload\n\n")
                                flush()
                                delay(INTERVAL_MS)
                            }
                        } catch (_: IOException) {
                            // Client disconnected; end the stream quietly.
                        }
                    }
                }

                // Static PWA + SPA fallback to index.html.
                get("/{path...}") {
                    val segments = call.parameters.getAll("path").orEmpty()
                    val rel = segments.joinToString("/")
                    serveAsset(rel)
                }
            }
        }.also { it.start(wait = false) }
    }

    fun stop() {
        engine?.stop(gracePeriodMillis = 200, timeoutMillis = 1_000)
        engine = null
    }

    private suspend fun io.ktor.server.application.ApplicationCall.serveAsset(relRaw: String) {
        val assets = context.assets
        // Normalize: "" or trailing slash → index.html of that dir.
        var rel = relRaw.trim('/')
        val candidates = buildList {
            if (rel.isEmpty()) add("web/index.html")
            else {
                add("web/$rel")
                add("web/$rel/index.html")
            }
            add("web/index.html") // SPA fallback
        }
        for (path in candidates) {
            val bytes = runCatching { assets.open(path).use { it.readBytes() } }.getOrNull()
            if (bytes != null) {
                respondBytes(bytes, contentTypeFor(path))
                return
            }
        }
        respond(HttpStatusCode.NotFound, "Not found")
    }

    private fun contentTypeFor(path: String): ContentType = when (path.substringAfterLast('.')) {
        "html" -> ContentType.Text.Html
        "js", "mjs" -> ContentType.Application.JavaScript
        "css" -> ContentType.Text.CSS
        "json", "webmanifest" -> ContentType.Application.Json
        "svg" -> ContentType.Image.SVG
        "png" -> ContentType.Image.PNG
        "jpg", "jpeg" -> ContentType.Image.JPEG
        "ico" -> ContentType.parse("image/x-icon")
        "woff2" -> ContentType.parse("font/woff2")
        "txt" -> ContentType.Text.Plain
        else -> ContentType.Application.OctetStream
    }

    companion object {
        const val DEFAULT_PORT = 8080
        private const val INTERVAL_MS = 3_000L
    }
}
