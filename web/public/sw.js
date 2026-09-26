// Minimal PWA service worker.
// - App shell: cache-first (works offline once loaded).
// - API + SSE: never cached (always hit the live on-device server).
const CACHE = "procmon-shell-v1";
const SHELL = ["/", "/index.html", "/manifest.webmanifest"];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE).then((c) => c.addAll(SHELL)).catch(() => {}),
  );
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))),
    ),
  );
  self.clients.claim();
});

self.addEventListener("fetch", (event) => {
  const url = new URL(event.request.url);

  // Live data must never be served stale.
  if (url.pathname.startsWith("/api/")) return;

  // Navigations & static assets: cache-first, fall back to network, then cache.
  event.respondWith(
    caches.match(event.request).then(
      (hit) =>
        hit ||
        fetch(event.request)
          .then((res) => {
            const copy = res.clone();
            if (res.ok && event.request.method === "GET") {
              caches.open(CACHE).then((c) => c.put(event.request, copy)).catch(() => {});
            }
            return res;
          })
          .catch(() => caches.match("/index.html")),
    ),
  );
});
