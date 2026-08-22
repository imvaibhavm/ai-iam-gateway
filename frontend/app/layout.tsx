import type { Metadata } from "next";
import "./globals.css";
import { IdentityAuthProvider } from "./auth-provider";

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
        <IdentityAuthProvider>{children}</IdentityAuthProvider>
      </body>
    </html>
  );
}
