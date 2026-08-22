"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { useIdentityAuth } from "../auth-provider";

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [error, setError] = useState("");
  const auth = useIdentityAuth();

  async function login() {
    const e = email.trim().toLowerCase();
    if (!e) return;

    setError("");
    try {
      const response = await auth.apiFetch("/auth/dev-token", {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: e, tenantId: "default" }),
      });
      if (!response.ok) throw new Error("Authentication failed");
      const data: { accessToken: string } = await response.json();
      localStorage.setItem("aiguard_access_token", data.accessToken);
      localStorage.setItem("aiguard_user_email", e);
      router.push("/");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Authentication failed");
    }
  }

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 flex items-center justify-center px-4">
      <div className="w-full max-w-md bg-zinc-900 border border-zinc-800 rounded-2xl p-8">
        <h1 className="text-2xl font-bold mb-2">Login</h1>
        <p className="text-zinc-400 mb-6">{auth.oidc
          ? "Authenticate with the configured enterprise identity provider."
          : "Enter your email for explicit local development authentication."}</p>

        {auth.oidc ? <button onClick={() => auth.login()}
          className="block text-center w-full bg-white text-black px-4 py-3 rounded-xl font-medium"
        >Login with Auth0</button> : <>

        <input
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="you@aiguard.com"
          className="w-full bg-zinc-950 border border-zinc-800 rounded-xl px-4 py-3 outline-none mb-4"
          onKeyDown={(e) => e.key === "Enter" && login()}
        />

        <button
          onClick={login}
          className="w-full bg-white text-black px-4 py-3 rounded-xl font-medium"
        >
          Continue
        </button>
        </>}

        {error && <div className="mt-4 text-sm text-red-400">{error}</div>}

        <div className="mt-4 text-xs text-zinc-500">
          {auth.oidc ? "Hosted OIDC mode · Authorization Code Flow with PKCE" : "Development JWT mode · Not for production"}
        </div>
      </div>
    </div>
  );
}
