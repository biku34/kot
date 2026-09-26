// Copies the static export (web/out) into the Android app's assets/web folder,
// which the embedded Ktor server serves. Run after `npm run build`.
//   node scripts/sync-to-android.mjs
import { cp, rm, stat } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const out = resolve(here, "..", "out");
const dest = resolve(here, "..", "..", "android", "app", "src", "main", "assets", "web");

try {
  await stat(out);
} catch {
  console.error("✗ web/out not found. Run `npm run build` first.");
  process.exit(1);
}

await rm(dest, { recursive: true, force: true });
await cp(out, dest, { recursive: true });
console.log(`✓ Synced web build → ${dest}`);
