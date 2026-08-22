import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "AI Security Gateway",
  description: "Identity and policy-aware AI security control plane",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className="antialiased">
        {children}
      </body>
    </html>
  );
}
