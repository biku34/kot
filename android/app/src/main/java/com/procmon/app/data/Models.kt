package com.procmon.app.data

import kotlinx.serialization.Serializable

/** Mirrors web/lib/types.ts — keep both in sync. */

@Serializable
enum class AppState { FOREGROUND, BACKGROUND, RECENT, UNKNOWN }

@Serializable
data class RunningApp(
    val packageName: String,
    val appLabel: String,
    val isSystemApp: Boolean,
    val state: AppState,
    val importance: Int? = null,
    val importanceLabel: String? = null,
    val memoryKb: Long? = null,
    val lastUsedMs: Long? = null,
    val totalForegroundMs: Long? = null,
)

@Serializable
data class DeviceMemory(
    val totalMemBytes: Long,
    val availMemBytes: Long,
    val lowMemory: Boolean,
    val thresholdBytes: Long,
)

@Serializable
data class Permissions(
    val usageStatsGranted: Boolean,
)

@Serializable
data class Snapshot(
    val collectedAt: Long,
    val windowMs: Long,
    val device: DeviceMemory,
    val permissions: Permissions,
    val count: Int,
    val apps: List<RunningApp>,
)
