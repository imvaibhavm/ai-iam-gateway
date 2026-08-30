"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useIdentityAuth } from "./auth-provider";

type Msg = {
  role: "user" | "assistant";
  content: string;
};

type UserRole = "INTERN" | "ENGINEER" | "FINANCE" | "ADMIN";
type WorkspaceMode = "chat" | "agent" | "tools";
type McpProvider = { id:string; name:string; category:string; description:string; requiredScope:string; tenantEnabled:boolean; userEnabled:boolean; connectionStatus:string };

export default function Home() {
  const router = useRouter();
  const auth = useIdentityAuth();
  const { apiFetch, authenticated, loading, oidc, userEmail: oidcUserEmail } = auth;
  const [userEmail, setUserEmail] = useState<string>("");
  const [meRole, setMeRole] = useState<UserRole | null>(null);
  const [identityError, setIdentityError] = useState("");

  const [messages, setMessages] = useState<Msg[]>([
    { role: "assistant", content: "Hi 👋 Ask me anything." },
  ]);
  const [input, setInput] = useState("");
  const [isStreaming, setIsStreaming] = useState(false);
  const [mode, setMode] = useState<WorkspaceMode>("chat");
  const [modeMenuOpen, setModeMenuOpen] = useState(false);
  const [toolPanelOpen, setToolPanelOpen] = useState(false);
  const [mcpProviders, setMcpProviders] = useState<McpProvider[]>([]);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const email = localStorage.getItem("aiguard_user_email") || "";
    const token = localStorage.getItem("aiguard_access_token") || "";
    if (loading) return;
    if ((oidc && !authenticated) || (!oidc && (!email || !token))) {
      router.push("/login");
      return;
    }

    setUserEmail(email || oidcUserEmail || "resolving identity…");
    // ✅ fetch role
    (async () => {
      try {
        const res = await apiFetch("/user/me");

        if (!res.ok) {
          setMeRole(null);
          setIdentityError(res.status === 403
            ? "Authenticated by Auth0, but this identity has no enabled local access mapping."
            : `Identity service returned ${res.status}.`);
          return;
        }

        const me = await res.json();
        setUserEmail(me.email);
        setMeRole(me.role);
        setIdentityError("");
        const mcpResponse = await apiFetch("/mcp/providers");
        if (mcpResponse.ok) setMcpProviders(await mcpResponse.json());
      } catch (cause) {
        setMeRole(null);
        setIdentityError(cause instanceof Error ? cause.message : "Identity resolution failed");
      }
    })();
  }, [apiFetch, authenticated, loading, oidc, oidcUserEmail, router]);

  const isAdmin = meRole === "ADMIN";

  const scrollToBottom = () => {
    setTimeout(() => bottomRef.current?.scrollIntoView({ behavior: "smooth" }), 50);
  };

  async function sendMessage() {
    const trimmed = input.trim();
    if (!trimmed || isStreaming) return;
    if (!userEmail) return;

    setInput("");
    setIsStreaming(true);

    setMessages((prev) => [
      ...prev,
      { role: "user", content: trimmed },
      { role: "assistant", content: "" },
    ]);
    scrollToBottom();

    const body = {
      sessionId: "s1",
      messages: [{ role: "user", content: trimmed }],
    };

    try {
      if (mode !== "chat") {
        const selectedTools = mcpProviders.filter(provider => provider.tenantEnabled && provider.userEnabled).map(provider => provider.id);
        const response = await auth.apiFetch("/agent/runs", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            prompt: trimmed,
            agentId: mode === "tools" ? "tool-assisted-agent" : "pr-review-agent",
            maxSteps: 8,
            requestedTools: selectedTools,
          }),
        });
        if (!response.ok) throw new Error("Agent execution failed safely");
        const run = await response.json();
        const text = run.status === "WAITING_APPROVAL"
          ? `Agent paused for a policy-required approval.\nRequest ID: ${run.requestId}\nAn administrator can review it in the Admin Console.`
          : (run.response || `Agent status: ${run.status}\nRequest ID: ${run.requestId}`);
        setMessages(prev => { const copy=[...prev]; copy[copy.length-1]={role:"assistant",content:text}; return copy; });
        return;
      }
      const resp = await auth.apiFetch("/chat/stream", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });

      if (!resp.ok || !resp.body) {
        throw new Error("Streaming failed");
      }

      const reader = resp.body.getReader();
      const decoder = new TextDecoder("utf-8");

      let buffer = "";
      let assistantText = "";

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });

        const parts = buffer.split("\n\n");
        buffer = parts.pop() ?? "";

        for (const p of parts) {
          const lines = p.split("\n");
          let eventType = "";
          let data = "";

          for (const line of lines) {
            if (line.startsWith("event:")) eventType = line.replace("event:", "").trim();
            if (line.startsWith("data:")) data += line.replace("data:", "");
          }

          if (eventType === "token") {
            assistantText += data;

            setMessages((prev) => {
              const copy = [...prev];
              copy[copy.length - 1] = { role: "assistant", content: assistantText };
              return copy;
            });

            scrollToBottom();
          }

          if (eventType === "done") {
            setIsStreaming(false);
          }
        }
      }
    } catch (e: unknown) {
      setMessages((prev) => [
        ...prev.slice(0, -1),
        {
          role: "assistant",
          content: "❌ Error streaming response: " + (e instanceof Error ? e.message : "unknown"),
        },
      ]);
    } finally {
      setIsStreaming(false);
      scrollToBottom();
    }
  }

  async function toggleMcp(provider: McpProvider) {
    if (!provider.tenantEnabled) return;
    const response = await auth.apiFetch(`/mcp/providers/${provider.id}/enabled/${!provider.userEnabled}`, { method: "PUT" });
    if (!response.ok) return;
    const updated = await response.json();
    setMcpProviders(current => current.map(item => item.id === updated.id ? updated : item));
  }

  function logout() {
    localStorage.removeItem("aiguard_user_email");
    localStorage.removeItem("aiguard_access_token");
    if (auth.oidc) auth.logout(); else router.push("/login");
  }

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 flex flex-col">
      <header className="p-4 border-b border-zinc-800 flex items-center justify-between">
        <div className="flex flex-col">
          <div className="flex items-center gap-2"><span className="h-7 w-7 rounded-lg bg-gradient-to-br from-cyan-300 to-emerald-400 text-zinc-950 flex items-center justify-center text-xs font-black">F</span><span className="text-lg font-semibold">Friday Workspace</span></div>
          <div className="text-xs text-zinc-400">
            {userEmail ? `Logged in as: ${userEmail}` : "Not logged in"}
            {meRole ? ` • Role: ${meRole}` : ""}
          </div>
          {identityError && <div className="text-xs text-red-400">Identity error: {identityError}</div>}
        </div>

        <div className="flex gap-2">
          {/* ✅ Only ADMIN can see Admin button */}
          {isAdmin && (
            <button
              onClick={() => router.push("/admin")}
              className="text-sm px-3 py-2 rounded-xl bg-zinc-800 hover:bg-zinc-700"
            >
              Admin
            </button>
          )}

          <button
            onClick={logout}
            className="text-sm px-3 py-2 rounded-xl bg-white text-black font-medium"
          >
            Logout
          </button>
        </div>
      </header>

      <main className="flex-1 overflow-auto p-4 space-y-4">
        {messages.map((m, idx) => (
          <div
            key={idx}
            className={`max-w-3xl px-4 py-3 rounded-2xl whitespace-pre-wrap leading-relaxed ${
              m.role === "user"
                ? "ml-auto bg-zinc-800"
                : "mr-auto bg-zinc-900 border border-zinc-800"
            }`}
          >
            {m.content || (m.role === "assistant" && isStreaming ? "..." : "")}
          </div>
        ))}
        <div ref={bottomRef} />
      </main>

      {toolPanelOpen && <div className="border-t border-zinc-800 bg-zinc-950 px-4 py-4">
        <div className="mx-auto max-w-5xl"><div className="flex items-start justify-between gap-4 mb-4"><div><div className="text-sm font-semibold">Available tool connections</div><div className="text-xs text-zinc-500 mt-1">You can enable only connections approved by your workspace administrator.</div></div><button onClick={()=>setToolPanelOpen(false)} className="text-xs text-zinc-500 hover:text-white">Close</button></div>
          <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-4">{mcpProviders.map(provider=><button key={provider.id} disabled={!provider.tenantEnabled} onClick={()=>void toggleMcp(provider)} className={`rounded-xl border p-3 text-left transition ${provider.userEnabled?'border-emerald-400/30 bg-emerald-400/[0.07]':provider.tenantEnabled?'border-zinc-800 bg-zinc-900 hover:border-zinc-700':'border-zinc-900 bg-zinc-950 opacity-45'}`}><div className="flex justify-between gap-2"><span className="text-sm font-medium">{provider.name}</span><span className={`h-2 w-2 mt-1 rounded-full ${provider.userEnabled?'bg-emerald-400':'bg-zinc-700'}`}/></div><div className="mt-1 text-[10px] text-zinc-600">{provider.tenantEnabled?(provider.userEnabled?'Enabled for you':'Available to enable'):'Not approved by admin'}</div></button>)}</div>
        </div>
      </div>}

      <footer className="p-4 border-t border-zinc-800 flex gap-2 bg-zinc-950/95">
        <div className="relative">
          <button onClick={() => setModeMenuOpen(value=>!value)} className={`min-w-24 px-4 py-3 rounded-xl border flex items-center justify-between gap-3 ${mode==='chat'?'border-zinc-700 text-zinc-300':mode==='agent'?'border-amber-500/50 text-amber-300':'border-emerald-500/50 text-emerald-300'}`}><span className="capitalize">{mode}</span><span className="text-[10px]">⌃</span></button>
          {modeMenuOpen && <div className="absolute bottom-full left-0 mb-2 w-72 overflow-hidden rounded-xl border border-zinc-700 bg-zinc-900 shadow-2xl"><div className="p-2">{[
            ['chat','Chat','Ask a model through Friday security controls.'],['agent','Create agent task','Run a governed multi-step workflow.'],['tools','Call tools','Let an agent propose approved tool actions.']
          ].map(([id,label,description])=><button key={id} onClick={()=>{setMode(id as WorkspaceMode);setModeMenuOpen(false);if(id==='tools')setToolPanelOpen(true)}} className={`w-full rounded-lg p-3 text-left hover:bg-zinc-800 ${mode===id?'bg-zinc-800':''}`}><div className="text-sm font-medium">{label}</div><div className="mt-1 text-[11px] leading-relaxed text-zinc-500">{description}</div></button>)}</div><button onClick={()=>{setModeMenuOpen(false);setToolPanelOpen(true)}} className="w-full border-t border-zinc-800 px-4 py-3 text-left text-xs text-cyan-300">Manage my tool connections →</button></div>}
        </div>
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && sendMessage()}
          placeholder={mode === "agent" ? "Review change #382 and merge it if everything looks safe." : mode === "tools" ? "Search the approved tools for the information I need…" : "Type your message..."}
          className="flex-1 bg-zinc-900 border border-zinc-700 rounded-xl px-4 py-3 outline-none"
          disabled={!userEmail}
        />

        <button
          onClick={sendMessage}
          disabled={isStreaming || !userEmail}
          className="bg-white text-black px-5 py-3 rounded-xl font-medium disabled:opacity-50"
        >
          Send
        </button>
      </footer>
    </div>
  );
}
