"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { AppState, RunningApp, Snapshot } from "@/lib/types";
import { fetchSnapshot, requestUsageAccess, streamSnapshots } from "@/lib/api";
import { ago, bytes, duration, kb } from "@/lib/format";

type Filter = "all" | "foreground" | "background" | "user";
type SortKey = "recent" | "name" | "memory" | "foregroundTime";

const STATE_ORDER: Record<AppState, number> = {
  FOREGROUND: 0,
  BACKGROUND: 1,
  RECENT: 2,
  UNKNOWN: 3,
};

export default function Page() {
  const [snap, setSnap] = useState<Snapshot | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [live, setLive] = useState(true);
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<Filter>("all");
  const [sort, setSort] = useState<SortKey>("recent");
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const load = useCallback(async () => {
    try {
      const s = await fetchSnapshot();
      setSnap(s);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Cannot reach the on-device server");
    } finally {
      setLoading(false);
    }
  }, []);

  // Live via SSE when enabled; otherwise a single load. Poll as SSE fallback.
  useEffect(() => {
    let stopStream: (() => void) | null = null;
    if (pollRef.current) clearInterval(pollRef.current);

    if (live) {
      let gotEvent = false;
      stopStream = streamSnapshots(
        (s) => {
          gotEvent = true;
          setSnap(s);
          setError(null);
          setLoading(false);
        },
        () => {
          // SSE failed → degrade to polling every 4s.
          if (!pollRef.current) {
            pollRef.current = setInterval(load, 4000);
          }
        },
      );
      // Kick an immediate fetch so we render before the first SSE frame.
      if (!gotEvent) void load();
    } else {
      void load();
    }

    return () => {
      stopStream?.();
      if (pollRef.current) {
        clearInterval(pollRef.current);
        pollRef.current = null;
      }
    };
  }, [live, load]);

  const granted = snap?.permissions.usageStatsGranted ?? true;

  const apps = useMemo(() => {
    let list = snap?.apps ?? [];
    const q = query.trim().toLowerCase();
    if (q) {
      list = list.filter(
        (a) =>
          a.appLabel.toLowerCase().includes(q) ||
          a.packageName.toLowerCase().includes(q),
      );
    }
    if (filter === "foreground") list = list.filter((a) => a.state === "FOREGROUND");
    if (filter === "background") list = list.filter((a) => a.state === "BACKGROUND");
    if (filter === "user") list = list.filter((a) => !a.isSystemApp);

    const sorted = [...list];
    sorted.sort((a, b) => {
      switch (sort) {
        case "name":
          return a.appLabel.localeCompare(b.appLabel);
        case "memory":
          return (b.memoryKb ?? -1) - (a.memoryKb ?? -1);
        case "foregroundTime":
          return (b.totalForegroundMs ?? 0) - (a.totalForegroundMs ?? 0);
        case "recent":
        default: {
          const s = STATE_ORDER[a.state] - STATE_ORDER[b.state];
          if (s !== 0) return s;
          return (b.lastUsedMs ?? 0) - (a.lastUsedMs ?? 0);
        }
      }
    });
    return sorted;
  }, [snap, query, filter, sort]);

  const fgCount = snap?.apps.filter((a) => a.state === "FOREGROUND").length ?? 0;
  const bgCount = snap?.apps.filter((a) => a.state === "BACKGROUND").length ?? 0;
  const mem = snap?.device;
  const usedPct =
    mem && mem.totalMemBytes > 0
      ? Math.round(((mem.totalMemBytes - mem.availMemBytes) / mem.totalMemBytes) * 100)
      : 0;

  return (
    <main className="app">
      <div className="header">
        <div>
          <h1 className="title">
            <span className={`dot ${live ? "" : "stale"}`} />
            ProcMon
          </h1>
          <div className="subtitle">
            {snap
              ? `${snap.count} apps · updated ${ago(snap.collectedAt)}`
              : "connecting to device…"}
          </div>
        </div>
        <button className="btn ghost" onClick={() => setLive((v) => !v)}>
          {live ? "Pause" : "Go live"}
        </button>
      </div>

      {error && (
        <div className="notice err">
          <h3>Can’t reach the device server</h3>
          <p>{error}. Make sure the ProcMon Android app is running its server.</p>
          <button className="btn" onClick={() => void load()}>
            Retry
          </button>
        </div>
      )}

      {!granted && (
        <div className="notice">
          <h3>Usage access needed</h3>
          <p>
            Android only reveals which apps are running/recent to apps granted{" "}
            <strong>Usage access</strong>. Grant it once and this list fills in.
          </p>
          <button
            className="btn"
            onClick={async () => {
              await requestUsageAccess();
            }}
          >
            Open Usage Access settings
          </button>
        </div>
      )}

      <div className="stats">
        <div className="stat">
          <div className="k">Foreground</div>
          <div className="v">{fgCount}</div>
        </div>
        <div className="stat">
          <div className="k">Background</div>
          <div className="v">{bgCount}</div>
        </div>
        <div className="stat">
          <div className="k">RAM used</div>
          <div className="v">
            {usedPct}
            <small>%</small>
          </div>
          <div className="membar">
            <span style={{ width: `${usedPct}%` }} />
          </div>
        </div>
      </div>

      {mem && (
        <div className="subtitle" style={{ marginBottom: 12 }}>
          {bytes(mem.totalMemBytes - mem.availMemBytes)} / {bytes(mem.totalMemBytes)} used
          {mem.lowMemory ? " · ⚠ low memory" : ""}
        </div>
      )}

      <div className="controls">
        <input
          className="search"
          placeholder="Search apps or packages…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <select
          className="search"
          style={{ flex: "0 0 auto" }}
          value={sort}
          onChange={(e) => setSort(e.target.value as SortKey)}
          aria-label="Sort by"
        >
          <option value="recent">Recent first</option>
          <option value="name">Name</option>
          <option value="memory">Memory</option>
          <option value="foregroundTime">Screen time</option>
        </select>
      </div>

      <div className="controls">
        <div className="seg">
          {(["all", "foreground", "background", "user"] as Filter[]).map((f) => (
            <button
              key={f}
              className={filter === f ? "active" : ""}
              onClick={() => setFilter(f)}
            >
              {f === "user" ? "User apps" : f[0].toUpperCase() + f.slice(1)}
            </button>
          ))}
        </div>
      </div>

      <div className="list">
        {loading && !snap ? (
          Array.from({ length: 6 }).map((_, i) => <div key={i} className="skeleton" />)
        ) : apps.length === 0 ? (
          <div className="empty">No apps match.</div>
        ) : (
          apps.map((a) => <AppRow key={a.packageName} app={a} />)
        )}
      </div>
    </main>
  );
}

function AppRow({ app }: { app: RunningApp }) {
  const initial = (app.appLabel || app.packageName).trim()[0]?.toUpperCase() ?? "?";
  return (
    <div className="row">
      <div className="icon">{initial}</div>
      <div className="main">
        <div className="name">
          {app.appLabel}
          {app.isSystemApp && <span className="tag-sys">system</span>}
        </div>
        <div className="pkg">{app.packageName}</div>
      </div>
      <div className="meta">
        <span className={`badge ${app.state}`}>{app.state.toLowerCase()}</span>
        <div style={{ marginTop: 6 }}>
          {app.memoryKb != null ? kb(app.memoryKb) : ago(app.lastUsedMs)}
        </div>
        {app.totalForegroundMs != null && app.totalForegroundMs > 0 && (
          <div style={{ color: "var(--text-faint)", fontSize: 11 }}>
            {duration(app.totalForegroundMs)} screen
          </div>
        )}
      </div>
    </div>
  );
}
