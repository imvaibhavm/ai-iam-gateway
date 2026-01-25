"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";

type Msg = {
  role: "user" | "assistant";
  content: string;
};

export default function Home() {
  const router = useRouter();
  const backend = process.env.NEXT_PUBLIC_BACKEND_URL || "http://localhost:8080";

  const [userEmail, setUserEmail] = useState<string>("");
  const [messages, setMessages] = useState<Msg[]>([
    { role: "assistant", content: "Hi 👋 Ask me anything." },
  ]);
  const [input, setInput] = useState("");
  const [isStreaming, setIsStreaming] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const email = localStorage.getItem("aiguard_user_email");
    if (!email) {
      router.push("/login");
      return;
    }
    setUserEmail(email);
  }, [router]);

  const scrollToBottom = () => {
    setTimeout(() => bottomRef.current?.scrollIntoView({ behavior: "smooth" }), 50);
  };

  async function sendMessage() {
    const trimmed = input.trim();
    if (!trimmed || isStreaming) return;
    if (!userEmail) return; // not logged in yet

    setInput("");
    setIsStreaming(true);

    // Add user message + assistant placeholder
    setMessages((prev) => [...prev, { role: "user", content: trimmed }, { role: "assistant", content: "" }]);
    scrollToBottom();

    const body = {
      sessionId: "s1",
      userId: userEmail,
      messages: [{ role: "user", content: trimmed }],
    };

    try {
      const resp = await fetch(`${backend}/api/chat/stream`, {
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

        // SSE messages separated by \n\n
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
    } catch (e: any) {
      setMessages((prev) => [
        ...prev.slice(0, -1),
        { role: "assistant", content: "❌ Error streaming response: " + (e?.message || "unknown") },
      ]);
    } finally {
      setIsStreaming(false);
      scrollToBottom();
    }
  }

  function logout() {
    localStorage.removeItem("aiguard_user_email");
    router.push("/login");
  }

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 flex flex-col">
      {/* Header */}
      <header className="p-4 border-b border-zinc-800 flex items-center justify-between">
        <div className="flex flex-col">
          <div className="text-lg font-semibold">AI Security Gateway Chat (POC)</div>
          <div className="text-xs text-zinc-400">{userEmail ? `Logged in as: ${userEmail}` : "Not logged in"}</div>
        </div>

        <div className="flex gap-2">
          <button
            onClick={() => router.push("/admin")}
            className="text-sm px-3 py-2 rounded-xl bg-zinc-800 hover:bg-zinc-700"
          >
            Admin
          </button>

          <button
            onClick={logout}
            className="text-sm px-3 py-2 rounded-xl bg-white text-black font-medium"
          >
            Logout
          </button>
        </div>
      </header>

      {/* Chat */}
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

      {/* Composer */}
      <footer className="p-4 border-t border-zinc-800 flex gap-2">
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && sendMessage()}
          placeholder="Type your message..."
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
