/** @type {import('next').NextConfig} */
const nextConfig = {
  // Static export: produces ./out with plain HTML/JS/CSS so the Android app's
  // Ktor server can serve it straight from assets/ (no Node runtime on device).
  output: "export",
  // No Next.js image optimizer available in a static export.
  images: { unoptimized: true },
  // Same-origin with the API (Ktor serves both), so no basePath needed.
  trailingSlash: true,
  reactStrictMode: true,
};

module.exports = nextConfig;
