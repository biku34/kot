import type { Metadata, Viewport } from "next";
import "./globals.css";
import { SWRegister } from "./sw-register";

export const metadata: Metadata = {
  title: "ProcMon",
  description: "Running & background apps on this device",
  manifest: "/manifest.webmanifest",
  appleWebApp: { capable: true, statusBarStyle: "black-translucent", title: "ProcMon" },
};

export const viewport: Viewport = {
  themeColor: "#0b0f14",
  width: "device-width",
  initialScale: 1,
  maximumScale: 1,
  viewportFit: "cover",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <SWRegister />
        {children}
      </body>
    </html>
  );
}
