"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");

  function login() {
    const e = email.trim().toLowerCase();
    if (!e) return;

    localStorage.setItem("aiguard_user_email", e);
    router.push("/");
  }

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 flex items-center justify-center px-4">
      <div className="w-full max-w-md bg-zinc-900 border border-zinc-800 rounded-2xl p-8">
        <h1 className="text-2xl font-bold mb-2">Login</h1>
        <p className="text-zinc-400 mb-6">
          Enter your email to access AI Security Gateway Chat.
        </p>

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

        <div className="mt-4 text-xs text-zinc-500">
          POC login only. Later: Google OAuth / Okta / Azure AD.
        </div>
      </div>
    </div>
  );
}
