"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";

type UserRole = "INTERN" | "ENGINEER" | "FINANCE" | "ADMIN";

type AppUser = {
  email: string;
  role: UserRole;
  enabled: boolean;
};

type AuditLog = {
  id: string;
  ts: string;
  userEmail: string;
  role: UserRole;
  intent: string;
  confidence: number;
  allowed: boolean;
  decisionReason: string;
  piiTypes: string;
  provider: string;
};

const roles: UserRole[] = ["INTERN", "ENGINEER", "FINANCE", "ADMIN"];

export default function AdminPage() {
  const backend = process.env.NEXT_PUBLIC_BACKEND_URL || "http://localhost:8080";
  const router = useRouter();

  const [users, setUsers] = useState<AppUser[]>([]);
  const [audits, setAudits] = useState<AuditLog[]>([]);
  const [loadingUsers, setLoadingUsers] = useState(false);
  const [loadingAudits, setLoadingAudits] = useState(false);
  const [err, setErr] = useState<string>("");

  const [newEmail, setNewEmail] = useState("");
  const [newRole, setNewRole] = useState<UserRole>("INTERN");

  function getEmailOrRedirect(): string | null {
    const email = localStorage.getItem("aiguard_user_email") || "";
    if (!email) {
      router.push("/login");
      return null;
    }
    return email;
  }

  async function requireAdmin() {
    const email = getEmailOrRedirect();
    if (!email) return;

    const res = await fetch(`${backend}/api/user/me`, {
      headers: { "X-User-Email": email },
    });

    if (!res.ok) {
      router.push("/");
      return;
    }

    const me = await res.json();
    if (me.role !== "ADMIN") {
      router.push("/");
    }
  }

  async function fetchUsers() {
    const email = getEmailOrRedirect();
    if (!email) return;

    setLoadingUsers(true);
    setErr("");
    try {
      const res = await fetch(`${backend}/api/admin/users`, {
        headers: { "X-User-Email": email },
      });

      if (!res.ok) throw new Error("Failed to load users");
      const data = await res.json();
      setUsers(data);
    } catch (e: any) {
      setErr(e.message || "Error");
    } finally {
      setLoadingUsers(false);
    }
  }

  async function fetchAudits() {
    const email = getEmailOrRedirect();
    if (!email) return;

    setLoadingAudits(true);
    setErr("");
    try {
      const res = await fetch(`${backend}/api/admin/audit?limit=100`, {
        headers: { "X-User-Email": email },
      });

      if (!res.ok) throw new Error("Failed to load audit logs");
      const data = await res.json();
      setAudits(data);
    } catch (e: any) {
      setErr(e.message || "Error");
    } finally {
      setLoadingAudits(false);
    }
  }

  useEffect(() => {
    requireAdmin().then(() => {
      fetchUsers();
      fetchAudits();
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function updateRole(emailToUpdate: string, role: UserRole) {
    const email = getEmailOrRedirect();
    if (!email) return;

    await fetch(`${backend}/api/admin/users/${encodeURIComponent(emailToUpdate)}/role/${role}`, {
      method: "PUT",
      headers: { "X-User-Email": email },
    });

    await fetchUsers();
  }

  async function updateEnabled(emailToUpdate: string, enabled: boolean) {
    const email = getEmailOrRedirect();
    if (!email) return;

    await fetch(
      `${backend}/api/admin/users/${encodeURIComponent(emailToUpdate)}/enabled/${enabled}`,
      {
        method: "PUT",
        headers: { "X-User-Email": email },
      }
    );

    await fetchUsers();
  }

  async function addUser() {
    const email = getEmailOrRedirect();
    if (!email) return;

    if (!newEmail.trim()) return;

    await fetch(`${backend}/api/admin/users`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-User-Email": email,
      },
      body: JSON.stringify({
        email: newEmail.trim().toLowerCase(),
        role: newRole,
        enabled: true,
      }),
    });

    setNewEmail("");
    setNewRole("INTERN");
    await fetchUsers();
  }

  function badge(ok: boolean) {
    return ok ? "border-green-600 text-green-400" : "border-red-600 text-red-400";
  }

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100">
      <div className="max-w-6xl mx-auto px-6 py-10">
        <div className="flex items-center justify-between mb-8">
          <h1 className="text-2xl font-bold">Admin Console</h1>

          <div className="flex gap-2">
  <button
    onClick={() => router.push("/")}
    className="px-4 py-2 rounded-xl bg-zinc-800 hover:bg-zinc-700"
  >
    Back to Chat
  </button>

  <button
    onClick={() => {
      localStorage.removeItem("aiguard_user_email");
      router.push("/login");
    }}
    className="px-4 py-2 rounded-xl bg-white text-black font-medium"
  >
    Logout
  </button>

  <button
    onClick={fetchUsers}
    className="px-4 py-2 rounded-xl bg-zinc-800 hover:bg-zinc-700"
  >
    Refresh Users
  </button>

  <button
    onClick={fetchAudits}
    className="px-4 py-2 rounded-xl bg-zinc-800 hover:bg-zinc-700"
  >
    Refresh Audit
  </button>
</div>

        </div>

        {err && <div className="mb-6 text-red-400">{err}</div>}

        {/* Add user */}
        <div className="bg-zinc-900 border border-zinc-800 rounded-2xl p-6 mb-8">
          <h2 className="text-lg font-semibold mb-4">Add / Upsert User</h2>

          <div className="flex gap-3 flex-wrap">
            <input
              value={newEmail}
              onChange={(e) => setNewEmail(e.target.value)}
              placeholder="user@aiguard.com"
              className="flex-1 min-w-[260px] bg-zinc-950 border border-zinc-800 rounded-xl px-4 py-3 outline-none"
            />

            <select
              value={newRole}
              onChange={(e) => setNewRole(e.target.value as UserRole)}
              className="bg-zinc-950 border border-zinc-800 rounded-xl px-4 py-3"
            >
              {roles.map((r) => (
                <option key={r} value={r}>
                  {r}
                </option>
              ))}
            </select>

            <button
              onClick={addUser}
              className="px-5 py-3 rounded-xl bg-white text-black font-medium"
            >
              Save
            </button>
          </div>
        </div>

        {/* Users */}
        <div className="bg-zinc-900 border border-zinc-800 rounded-2xl p-6 mb-10">
          <h2 className="text-lg font-semibold mb-4">Users</h2>

          {loadingUsers && <div className="mb-4 text-zinc-400">Loading users...</div>}

          <div className="overflow-auto">
            <table className="w-full text-sm">
              <thead className="text-zinc-400">
                <tr className="border-b border-zinc-800">
                  <th className="text-left py-3 pr-4">Email</th>
                  <th className="text-left py-3 pr-4">Role</th>
                  <th className="text-left py-3 pr-4">Enabled</th>
                </tr>
              </thead>
              <tbody>
                {users.map((u) => (
                  <tr key={u.email} className="border-b border-zinc-900">
                    <td className="py-3 pr-4">{u.email}</td>

                    <td className="py-3 pr-4">
                      <select
                        value={u.role}
                        onChange={(e) => updateRole(u.email, e.target.value as UserRole)}
                        className="bg-zinc-950 border border-zinc-800 rounded-xl px-3 py-2"
                      >
                        {roles.map((r) => (
                          <option key={r} value={r}>
                            {r}
                          </option>
                        ))}
                      </select>
                    </td>

                    <td className="py-3 pr-4">
                      <button
                        onClick={() => updateEnabled(u.email, !u.enabled)}
                        className={`px-4 py-2 rounded-xl border ${badge(u.enabled)}`}
                      >
                        {u.enabled ? "Enabled" : "Disabled"}
                      </button>
                    </td>
                  </tr>
                ))}
                {users.length === 0 && !loadingUsers && (
                  <tr>
                    <td className="py-6 text-zinc-400" colSpan={3}>
                      No users found.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* Audit Logs */}
        <div className="bg-zinc-900 border border-zinc-800 rounded-2xl p-6">
          <h2 className="text-lg font-semibold mb-4">Audit Logs (latest 100)</h2>

          {loadingAudits && <div className="mb-4 text-zinc-400">Loading audit logs...</div>}

          <div className="overflow-auto">
            <table className="w-full text-xs">
              <thead className="text-zinc-400">
                <tr className="border-b border-zinc-800">
                  <th className="text-left py-3 pr-3">Time</th>
                  <th className="text-left py-3 pr-3">User</th>
                  <th className="text-left py-3 pr-3">Role</th>
                  <th className="text-left py-3 pr-3">Intent</th>
                  <th className="text-left py-3 pr-3">Conf</th>
                  <th className="text-left py-3 pr-3">Allowed</th>
                  <th className="text-left py-3 pr-3">Reason</th>
                  <th className="text-left py-3 pr-3">PII Types</th>
                  <th className="text-left py-3 pr-3">Provider</th>
                </tr>
              </thead>
              <tbody>
                {audits.map((a) => (
                  <tr key={a.id} className="border-b border-zinc-900">
                    <td className="py-3 pr-3 text-zinc-300">
                      {a.ts ? new Date(a.ts).toLocaleString() : "-"}
                    </td>
                    <td className="py-3 pr-3">{a.userEmail}</td>
                    <td className="py-3 pr-3">{a.role}</td>
                    <td className="py-3 pr-3">{a.intent}</td>
                    <td className="py-3 pr-3">{a.confidence?.toFixed?.(2) ?? "-"}</td>
                    <td className="py-3 pr-3">
                      <span className={`px-2 py-1 rounded-lg border ${badge(a.allowed)}`}>
                        {a.allowed ? "ALLOW" : "DENY"}
                      </span>
                    </td>
                    <td className="py-3 pr-3 text-zinc-300">{a.decisionReason}</td>
                    <td className="py-3 pr-3 text-zinc-300">{a.piiTypes || "-"}</td>
                    <td className="py-3 pr-3 text-zinc-300">{a.provider}</td>
                  </tr>
                ))}

                {audits.length === 0 && !loadingAudits && (
                  <tr>
                    <td className="py-6 text-zinc-400" colSpan={9}>
                      No audit logs yet.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="mt-4 text-zinc-500 text-sm">
            Audit logs prove governance: every request → intent + pii + allow/deny decision stored.
          </div>
        </div>
      </div>
    </div>
  );
}
