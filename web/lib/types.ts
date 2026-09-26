// Shared contract between the PWA and the Kotlin/Ktor backend.
// Keep this in sync with android/.../data/Models.kt

export type AppState = "FOREGROUND" | "BACKGROUND" | "RECENT" | "UNKNOWN";

export interface RunningApp {
  packageName: string;
  appLabel: string;
  isSystemApp: boolean;
  state: AppState;
  /** ActivityManager importance code, when the OS exposes it (often only our own). */
  importance: number | null;
  importanceLabel: string | null;
  /** Best-effort RSS in KB; usually null on non-rooted modern Android. */
  memoryKb: number | null;
  /** Epoch millis of last foreground use (UsageStats), or null. */
  lastUsedMs: number | null;
  /** Total foreground time in the sampling window, ms. */
  totalForegroundMs: number | null;
}

export interface DeviceMemory {
  totalMemBytes: number;
  availMemBytes: number;
  lowMemory: boolean;
  thresholdBytes: number;
}

export interface Snapshot {
  collectedAt: number; // epoch millis
  windowMs: number; // how far back UsageStats was queried
  device: DeviceMemory;
  permissions: {
    usageStatsGranted: boolean;
  };
  count: number;
  apps: RunningApp[];
}
