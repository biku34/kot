"use client";

import { useEffect } from "react";

export function SWRegister() {
  useEffect(() => {
    if (typeof window === "undefined") return;
    if (!("serviceWorker" in navigator)) return;
    // localhost is a secure context, so the SW registers even without HTTPS.
    navigator.serviceWorker.register("/sw.js").catch(() => {
      /* offline shell just won't be available; app still works online */
    });
  }, []);
  return null;
}
