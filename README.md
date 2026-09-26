# ProcMon — PWA + Android-native app monitor

A **PWA frontend** (Next.js) that lists the apps currently running in the
**foreground / background / recently used** on your Android phone, fed by a
**Kotlin backend** running inside a native Android app.

The embedded Ktor server serves **both** the PWA and the JSON API on the same
loopback origin, so the browser makes same-origin requests with no CORS issues.

```
PWA (Next.js static export)  ──GET /api/apps (JSON)──▶  Android app (Kotlin)
installable from Chrome      ──GET /api/stream (SSE)─▶   • Ktor CIO server (serves PWA + API)
http://localhost:8080        ◀──────── snapshots ─────   • AppCollector: UsageStatsManager + ActivityManager
                                                          • foreground Service keeps it alive
```

## Honest limitations (read this first)

- **A pure PWA cannot read OS processes** — browsers sandbox web code. All data
  comes from the native Kotlin side; the PWA only renders it.
- **Android hides most processes.** Since API 21, `getRunningAppProcesses()`
  returns essentially only this app. Without root you cannot enumerate kernel
  processes. This app reports **apps** (foreground / background / recently used),
  inferred from `UsageStatsManager` events + `ActivityManager` memory — what a
  stock, unrooted device is allowed to expose.
- **Per-app RSS is usually unavailable** on modern non-rooted Android, so memory
  is shown at the device level and per-app only when the OS exposes it.
- Requires the user to grant **Usage Access** once in system Settings.

## Layout

```
web/        Next.js PWA (static export → out/)
android/    Android app (Kotlin): Ktor server + UsageStats collector
```

## Build & run

### 1. Build the PWA and sync it into the app

```bash
cd web
npm install
npm run build          # produces web/out
npm run sync-android   # copies web/out → android/app/src/main/assets/web
```

### 2. Build & install the Android app

Open `android/` in Android Studio (or `./gradlew installDebug`), run it on a
device/emulator (minSdk 24). In the app:

1. Tap **Grant Usage Access** and enable ProcMon in the system list.
2. Tap **Start server** (also starts automatically on launch).
3. Tap **Open PWA in browser** → Chrome loads `http://localhost:8080`.
4. In Chrome, use **Add to Home screen** to install it as a PWA.

### Developing the PWA against a device

```bash
cd web
NEXT_PUBLIC_API_BASE="http://<phone-ip>:8080" npm run dev
```

For that, temporarily change the Ktor host from `127.0.0.1` to `0.0.0.0` in
`WebServer.kt` (revert for release; loopback-only is safer).

## API

| Endpoint                             | Description                                   |
| ------------------------------------ | --------------------------------------------- |
| `GET /api/apps`                      | One JSON `Snapshot`                           |
| `GET /api/stream`                    | SSE stream of `Snapshot` (event: `snapshot`)  |
| `POST /api/permissions/usage-access` | Opens the Usage Access settings screen        |
| `GET /health`                        | Liveness check                                |

The `Snapshot` shape is defined in `web/lib/types.ts` and mirrored in
`android/.../data/Models.kt`.

## Notes

- The PWA icon is an SVG. For stricter install prompts, add PNG icons (192/512)
  under `web/public/icons/` and list them in the manifest.
- `QUERY_ALL_PACKAGES` is declared to resolve app labels; remove it if shipping
  on Google Play and you only need your own package set.
