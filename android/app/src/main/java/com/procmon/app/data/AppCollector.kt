package com.procmon.app.data

import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.procmon.app.util.UsageAccess

/**
 * Collects the set of apps that are foreground / recently-in-background / recently
 * used on this device, using only non-root, no-special-hardware APIs:
 *
 *  - [UsageStatsManager] usage events → which app is foreground and when each was last used.
 *  - [ActivityManager] → device memory pressure and (best-effort) our own process importance.
 *
 * NOTE on Android's limits: since API 21, [ActivityManager.getRunningAppProcesses] returns
 * essentially only this app, so a true kernel process list is not available without root.
 * "Running/background" here is therefore inferred from recent foreground activity, which is
 * what a stock, unrooted device is allowed to report.
 */
class AppCollector(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val usm =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    /** Apps used more recently than this (and not foreground) are treated as BACKGROUND. */
    private val backgroundHorizonMs = 10 * 60_000L

    fun snapshot(windowMs: Long = 6 * 60 * 60_000L): Snapshot {
        val now = System.currentTimeMillis()
        val granted = UsageAccess.isGranted(context)

        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val device = DeviceMemory(
            totalMemBytes = mi.totalMem,
            availMemBytes = mi.availMem,
            lowMemory = mi.lowMemory,
            thresholdBytes = mi.threshold,
        )

        val apps = if (granted) collectApps(now, windowMs) else emptyList()

        return Snapshot(
            collectedAt = now,
            windowMs = windowMs,
            device = device,
            permissions = Permissions(usageStatsGranted = granted),
            count = apps.size,
            apps = apps,
        )
    }

    private fun collectApps(now: Long, windowMs: Long): List<RunningApp> {
        val from = now - windowMs

        // Per-package aggregates from the usage-events stream.
        data class Agg(var lastUsed: Long = 0, var fgTotal: Long = 0, var lastResume: Long = 0)
        val agg = HashMap<String, Agg>()
        var currentForeground: String? = null

        val events = usm.queryEvents(from, now)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            val a = agg.getOrPut(pkg) { Agg() }
            // MOVE_TO_FOREGROUND (1) == ACTIVITY_RESUMED, MOVE_TO_BACKGROUND (2) == ACTIVITY_PAUSED;
            // they are the same integer constants across API levels, so match the stable pair.
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    a.lastResume = e.timeStamp
                    a.lastUsed = maxOf(a.lastUsed, e.timeStamp)
                    currentForeground = pkg
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (a.lastResume > 0) {
                        a.fgTotal += (e.timeStamp - a.lastResume).coerceAtLeast(0)
                        a.lastResume = 0
                    }
                    a.lastUsed = maxOf(a.lastUsed, e.timeStamp)
                    if (currentForeground == pkg) currentForeground = null
                }
            }
        }
        // An app still resumed at snapshot time counts up to now.
        agg.values.forEach { if (it.lastResume > 0) it.fgTotal += (now - it.lastResume) }

        // Best-effort importance/RSS for whatever the OS still exposes (usually just us).
        val procInfo = runCatching { am.runningAppProcesses ?: emptyList() }.getOrDefault(emptyList())
        val importanceByPkg = HashMap<String, Int>()
        val rssByPkg = HashMap<String, Long>()
        for (p in procInfo) {
            val pids = intArrayOf(p.pid)
            val mem = runCatching { am.getProcessMemoryInfo(pids).firstOrNull() }.getOrNull()
            for (pkg in p.pkgList ?: arrayOf()) {
                importanceByPkg[pkg] = minOf(importanceByPkg[pkg] ?: Int.MAX_VALUE, p.importance)
                if (mem != null) rssByPkg[pkg] = (mem.totalPss.toLong() /* KB */)
            }
        }

        return agg.entries.mapNotNull { (pkg, a) ->
            val appInfo = resolveAppInfo(pkg) ?: return@mapNotNull null
            val importance = importanceByPkg[pkg]
            val state = when {
                pkg == currentForeground -> AppState.FOREGROUND
                importance != null &&
                    importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE ->
                    AppState.FOREGROUND
                a.lastUsed >= now - backgroundHorizonMs -> AppState.BACKGROUND
                importance != null -> AppState.BACKGROUND
                else -> AppState.RECENT
            }
            RunningApp(
                packageName = pkg,
                appLabel = runCatching { pm.getApplicationLabel(appInfo).toString() }
                    .getOrDefault(pkg),
                isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                state = state,
                importance = importance,
                importanceLabel = importance?.let(::importanceLabel),
                memoryKb = rssByPkg[pkg],
                lastUsedMs = a.lastUsed.takeIf { it > 0 },
                totalForegroundMs = a.fgTotal.takeIf { it > 0 },
            )
        }
    }

    private fun resolveAppInfo(pkg: String): ApplicationInfo? =
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(pkg, 0)
            }
        }.getOrNull()

    private fun importanceLabel(code: Int): String = when (code) {
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "foreground"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "foreground service"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "visible"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> "perceptible"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "service"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "cached"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE -> "gone"
        else -> "other ($code)"
    }
}
