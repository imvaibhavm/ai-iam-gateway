"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";

type UserRole = "INTERN" | "ENGINEER" | "FINANCE" | "ADMIN";

type AppUser = {
  email: string;
  role: UserRole;
  enabled: boolean;
};

const roles: UserRole[] = ["INTERN", "ENGINEER", "FINANCE", "ADMIN"];

export default function AdminPage() {
  const backend = process.env.NEXT_PUBLIC_BACKEND_URL || "http://localhost:8080";
  const router = useRouter();

  const [users, setUsers] = useState<AppUser[]>([]);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState<string>("");

  const [newEmail, setNewEmail] = useState("");
  const [newRole, setNewRole] = useState<UserRole>("INTERN");

  const [isAuthorized, setIsAuthorized] = useState(false);

  async function fetchUsers() {
    setLoading(true);
    setErr("");
    try {
      const res = await fetch(`${backend}/api/admin/users`);
      if (!res.ok) throw new Error("Failed to load users");
      const data = await res.json();
      setUsers(data);
    } catch (e: any) {
      setErr(e.message || "Error");
    } finally {
      setLoading(false);
    }
  }

  // ✅ Admin guard + initial load
  useEffect(() => {
    const email = localStorage.getItem("aiguard_user_email");
    if (!email) {
      router.push("/login");
      return;
    }

    (async () => {
      try {
        const res = await fetch(`${backend}/api/user/me`, {
          headers: { "X-User-Email": email },
        });

        if (!res.ok) {
          router.push("/");
          return;
        }

        const me: AppUser = await res.json();

        if (me.role !== "ADMIN") {
          router.push("/");
          return;
        }

        setIsAuthorized(true);
        fetchUsers(); // ✅ load users after authorization
      } catch (e) {
        router.push("/");
      }
    })();
  }, [router, backend]);

  async function updateRole(email: string, role: UserRole) {
    await fetch(`${backend}/api/admin/users/${encodeURIComponent(email)}/role/${role}`, {
      method: "PUT",
    });
    await fetchUsers();
  }

  async function updateEnabled(email: string, enabled: boolean) {
    await fetch(`${backend}/api/admin/users/${encodeURIComponent(email)}/enabled/${enabled}`, {
      method: "PUT",
    });
    await fetchUsers();
  }

  async function addUser() {
    if (!newEmail.trim()) return;

    await fetch(`${backend}/api/admin/users`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
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

  // ✅ avoid UI flash before auth check completes
  if (!isAuthorized) {
    return (
      <div className="min-h-screen bg-zinc-950 text-zinc-100 flex items-center justify-center">
        <div className="text-zinc-400">Checking admin access...</div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100">
      <div className="max-w-5xl mx-auto px-6 py-10">
        <div className="flex items-center justify-between mb-8">
          <h1 className="text-2xl font-bold">Admin Console</h1>
          <button
            onClick={fetchUsers}
            className="px-4 py-2 rounded-xl bg-zinc-800 hover:bg-zinc-700"
          >
            Refresh
          </button>
        </div>

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

        {/* Users table */}
        <div className="bg-zinc-900 border border-zinc-800 rounded-2xl p-6">
          <h2 className="text-lg font-semibold mb-4">Users</h2>

          {err && <div className="mb-4 text-red-400">{err}</div>}
          {loading && <div className="mb-4 text-zinc-400">Loading...</div>}

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
                        className={`px-4 py-2 rounded-xl border ${
                          u.enabled
                            ? "border-green-600 text-green-400"
                            : "border-red-600 text-red-400"
                        }`}
                      >
                        {u.enabled ? "Enabled" : "Disabled"}
                      </button>
                    </td>
                  </tr>
                ))}

                {users.length === 0 && !loading && (
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

        <div className="mt-6 text-zinc-500 text-sm">
          POC: Admin UI is protected by role-check. Next step: secure admin APIs too (backend 403 for non-admin).
        </div>
      </div>
    </div>
  );
}
