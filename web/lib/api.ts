import type { Snapshot } from "./types";

// The PWA is served by the same Ktor server that exposes the API, so relative
// URLs resolve to the on-device server (http://localhost:<port>).
// Override with NEXT_PUBLIC_API_BASE for `next dev` against a device/emulator.
const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? "";

export async function fetchSnapshot(signal?: AbortSignal): Promise<Snapshot> {
  const res = await fetch(`${API_BASE}/api/apps`, {
    signal,
    headers: { Accept: "application/json" },
    cache: "no-store",
  });
  if (!res.ok) throw new Error(`API ${res.status}: ${res.statusText}`);
  return (await res.json()) as Snapshot;
}

export async function requestUsageAccess(): Promise<void> {
  // Asks the native side to open the Usage Access settings screen.
  await fetch(`${API_BASE}/api/permissions/usage-access`, { method: "POST" });
}

/**
 * Subscribe to the server-sent-events stream of snapshots.
 * Falls back gracefully: the caller can poll fetchSnapshot() if this errors.
 */
export function streamSnapshots(
  onSnapshot: (s: Snapshot) => void,
  onError: (e: Event) => void,
): () => void {
  const es = new EventSource(`${API_BASE}/api/stream`);
  es.addEventListener("snapshot", (ev) => {
    try {
      onSnapshot(JSON.parse((ev as MessageEvent).data) as Snapshot);
    } catch {
      /* ignore malformed frame */
    }
  });
  es.onerror = onError;
  return () => es.close();
}
