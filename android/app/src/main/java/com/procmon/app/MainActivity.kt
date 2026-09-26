package com.procmon.app

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.procmon.app.server.ServerService
import com.procmon.app.server.WebServer
import com.procmon.app.util.UsageAccess

/**
 * Minimal control panel. The real UI is the PWA; this screen just starts the
 * on-device server, requests permissions, and opens the PWA in the browser.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private val url = "http://localhost:${WebServer.DEFAULT_PORT}"

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best-effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0b0f14"))
            setPadding(48, 96, 48, 48)
        }

        root.addView(title("ProcMon"))
        root.addView(body("On-device server + PWA for viewing running / background apps."))

        status = body("")
        root.addView(status)

        root.addView(button("Grant Usage Access") { UsageAccess.openSettings(this) })
        root.addView(button("Start server") {
            ServerService.start(this)
            refresh()
        })
        root.addView(button("Stop server") {
            ServerService.stop(this)
            refresh()
        })
        root.addView(button("Open PWA in browser") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        })

        setContentView(root)

        askNotificationPermission()
        ServerService.start(this)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val granted = UsageAccess.isGranted(this)
        status.text = buildString {
            append("Server URL: ").append(url).append("\n\n")
            append("Usage access: ").append(if (granted) "✓ granted" else "✗ not granted")
            if (!granted) append("\n\nTap “Grant Usage Access”, then reopen the PWA.")
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ---- tiny view helpers ----
    private fun title(t: String) = TextView(this).apply {
        text = t
        setTextColor(Color.parseColor("#e6edf3"))
        textSize = 28f
        setPadding(0, 0, 0, 16)
    }

    private fun body(t: String) = TextView(this).apply {
        text = t
        setTextColor(Color.parseColor("#93a1b0"))
        textSize = 15f
        setPadding(0, 0, 0, 24)
    }

    private fun button(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            bottomMargin = 20
        }
        gravity = Gravity.CENTER
    }
}
