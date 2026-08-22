"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useIdentityAuth } from "../auth-provider";

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

type ProviderStatus = {
  provider: string;
  model: string;
  cloud: boolean;
  available: boolean;
  reason: string;
};

type SecuritySummary = {
  total: number;
  denied: number;
  providerFailures: number;
  outputRedactions: number;
  byIntent: Record<string, number>;
};
type AgentRun = { id:string; requestId:string; tenantId:string; agentId:string; originatingSubject:string; stepCount:number; maxSteps:number; provider?:string; policyResult?:string; status:string };
type Approval = { id:string; requestId:string; toolName:string; sanitizedArguments:string; reason:string; risk:string; status:string; decidedBy?:string; decidedAt?:string };

function approvalTarget(approval: Approval) {
  try {
    const args = JSON.parse(approval.sanitizedArguments) as { repository?: string; repositoryUrl?: string; pullRequest?: number };
    const repository = args.repository || "imvaibhavm/ai-iam-gateway";
    const baseUrl = args.repositoryUrl || `https://github.com/${repository}`;
    return { repository, pullRequest: args.pullRequest, url: args.pullRequest ? `${baseUrl}/pull/${args.pullRequest}` : baseUrl };
  } catch {
    return { repository: "imvaibhavm/ai-iam-gateway", pullRequest: undefined, url: "https://github.com/imvaibhavm/ai-iam-gateway" };
  }
}

const roles: UserRole[] = ["INTERN", "ENGINEER", "FINANCE", "ADMIN"];

export default function AdminPage() {
  const router = useRouter();
  const auth = useIdentityAuth();

  const [users, setUsers] = useState<AppUser[]>([]);
  const [audits, setAudits] = useState<AuditLog[]>([]);
  const [providers, setProviders] = useState<ProviderStatus[]>([]);
  const [securitySummary, setSecuritySummary] = useState<SecuritySummary | null>(null);
  const [agentRuns, setAgentRuns] = useState<AgentRun[]>([]);
  const [approvals, setApprovals] = useState<Approval[]>([]);
  const [approvalHistory, setApprovalHistory] = useState<Approval[]>([]);
  const [loadingUsers, setLoadingUsers] = useState(false);
  const [loadingAudits, setLoadingAudits] = useState(false);
  const [err, setErr] = useState<string>("");

  const [newEmail, setNewEmail] = useState("");
  const [newRole, setNewRole] = useState<UserRole>("INTERN");

  function getEmailOrRedirect(): string | null {
    if (auth.oidc) return "oidc-session";
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

    const res = await auth.apiFetch("/user/me");

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
      const res = await auth.apiFetch("/admin/users");

      if (!res.ok) throw new Error("Failed to load users");
      const data = await res.json();
      setUsers(data);
    } catch (e: unknown) {
      setErr(e instanceof Error ? e.message : "Error");
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
      const res = await auth.apiFetch("/admin/audit?limit=100");

      if (!res.ok) throw new Error("Failed to load audit logs");
      const data = await res.json();
      setAudits(data);
    } catch (e: unknown) {
      setErr(e instanceof Error ? e.message : "Error");
    } finally {
      setLoadingAudits(false);
    }
  }

  async function fetchSecurityPlane() {
    const email = getEmailOrRedirect();
    if (!email) return;
    try {
      const [providerResponse, summaryResponse, runsResponse, approvalsResponse, approvalHistoryResponse] = await Promise.all([
        auth.apiFetch("/admin/providers"),
        auth.apiFetch("/admin/security-events/summary"),
        auth.apiFetch("/agent/runs"),
        auth.apiFetch("/admin/approvals"),
        auth.apiFetch("/admin/approvals/history"),
      ]);
      if (!providerResponse.ok || !summaryResponse.ok || !runsResponse.ok || !approvalsResponse.ok || !approvalHistoryResponse.ok) {
        throw new Error("Failed to load security-plane status");
      }
      setProviders(await providerResponse.json());
      setSecuritySummary(await summaryResponse.json());
      setAgentRuns(await runsResponse.json());
      setApprovals(await approvalsResponse.json());
      setApprovalHistory(await approvalHistoryResponse.json());
    } catch (e: unknown) {
      setErr(e instanceof Error ? e.message : "Error");
    }
  }

  useEffect(() => {
    requireAdmin().then(() => {
      fetchUsers();
      fetchAudits();
      fetchSecurityPlane();
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function updateRole(emailToUpdate: string, role: UserRole) {
    const email = getEmailOrRedirect();
    if (!email) return;

    await auth.apiFetch(`/admin/users/${encodeURIComponent(emailToUpdate)}/role/${role}`, {
      method: "PUT",
    });

    await fetchUsers();
  }

  async function updateEnabled(emailToUpdate: string, enabled: boolean) {
    const email = getEmailOrRedirect();
    if (!email) return;

    await auth.apiFetch(
      `/admin/users/${encodeURIComponent(emailToUpdate)}/enabled/${enabled}`,
      {
        method: "PUT",
      }
    );

    await fetchUsers();
  }

  async function addUser() {
    const email = getEmailOrRedirect();
    if (!email) return;

    if (!newEmail.trim()) return;

    await auth.apiFetch("/admin/users", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
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

  async function decideApproval(id: string, decision: "approve" | "reject") {
    const res = await auth.apiFetch(`/admin/approvals/${id}/${decision}`, {
      method: "POST",
    });
    if (!res.ok) setErr(`Approval ${decision} failed safely`);
    await fetchSecurityPlane();
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
      localStorage.removeItem("aiguard_access_token");
      if (auth.oidc) auth.logout(); else router.push("/login");
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

        <div className="bg-zinc-900 border border-zinc-800 rounded-2xl p-6 mb-8">
          <h2 className="text-lg font-semibold mb-4">PR Approval Audit</h2>
          <div className="overflow-auto"><table className="w-full text-xs">
            <thead className="text-zinc-400"><tr className="border-b border-zinc-800">
              {['Request','Repository / PR','Action','Risk','Decision','Decided by'].map(h => <th key={h} className="text-left py-3 pr-3">{h}</th>)}
            </tr></thead>
            <tbody>{approvalHistory.map(item => { const target=approvalTarget(item); return <tr key={item.id} className="border-b border-zinc-800/70">
              <td className="py-3 pr-3 font-mono">{item.requestId}</td>
              <td className="py-3 pr-3"><a href={target.url} target="_blank" rel="noreferrer" className="text-sky-400 hover:underline">{target.repository}{target.pullRequest ? ` #${target.pullRequest}` : ''}</a></td>
              <td className="py-3 pr-3">{item.toolName}</td><td className="py-3 pr-3">{item.risk}</td>
              <td className="py-3 pr-3">{item.status}</td><td className="py-3 pr-3">{item.decidedBy || '—'}</td>
            </tr>})}</tbody>
          </table></div>
          {approvalHistory.length === 0 && <div className="text-zinc-500">No PR approval decisions recorded.</div>}
        </div>

        {err && <div className="mb-6 text-red-400">{err}</div>}

        {/* Security plane overview */}
        <div className="grid gap-4 md:grid-cols-4 mb-8">
          {[
            ["Security events", securitySummary?.total ?? 0],
            ["Policy denials", securitySummary?.denied ?? 0],
            ["Provider failures", securitySummary?.providerFailures ?? 0],
            ["Output redactions", securitySummary?.outputRedactions ?? 0],
          ].map(([label, value]) => (
            <div key={String(label)} className="bg-zinc-900 border border-zinc-800 rounded-2xl p-5">
              <div className="text-sm text-zinc-400">{label}</div>
              <div className="text-3xl font-semibold mt-2">{value}</div>
            </div>
          ))}
        </div>

        <div className="bg-zinc-900 border border-zinc-800 rounded-2xl p-6 mb-8">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold">Model Providers</h2>
            <button onClick={fetchSecurityPlane} className="px-3 py-2 rounded-xl bg-zinc-800 hover:bg-zinc-700 text-sm">
              Refresh status
            </button>
          </div>
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            {providers.map((provider) => (
              <div key={provider.provider} className="rounded-xl border border-zinc-800 bg-zinc-950 p-4">
                <div className="flex items-center justify-between gap-3">
                  <span className="font-medium capitalize">{provider.provider}</span>
                  <span className={`text-xs border rounded-lg px-2 py-1 ${badge(provider.available)}`}>
                    {provider.available ? "AVAILABLE" : "OFFLINE"}
                  </span>
                </div>
                <div className="text-sm text-zinc-400 mt-2 break-all">{provider.model}</div>
                <div className="text-xs text-zinc-500 mt-2">
                  {provider.cloud ? "Cloud" : "Local"} · {provider.reason}
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="bg-zinc-900 border border-zinc-800 rounded-2xl p-6 mb-8">
          <h2 className="text-lg font-semibold mb-4">Pending Agent Approvals</h2>
          <div className="grid gap-3">
            {approvals.map((approval) => {
              const target = approvalTarget(approval);
              return <div key={approval.id} className="rounded-xl border border-amber-700/60 bg-zinc-950 p-4">
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div>
                    <div className="text-amber-400 font-medium">{approval.risk}-risk action</div>
                    <div className="mt-1">{approval.toolName}</div>
                    <div className="text-xs text-zinc-500 mt-1">Request {approval.requestId}</div>
                    <a href={target.url} target="_blank" rel="noreferrer" className="text-sm text-sky-400 hover:underline mt-2 inline-block">
                      {target.repository}{target.pullRequest ? ` · PR #${target.pullRequest}` : ""}
                    </a>
                    <div className="text-sm text-zinc-400 mt-2">{approval.sanitizedArguments}</div>
                  </div>
                  <div className="flex gap-2">
                    <button onClick={() => decideApproval(approval.id, "approve")} className="px-3 py-2 rounded-lg bg-green-700">Approve</button>
                    <button onClick={() => decideApproval(approval.id, "reject")} className="px-3 py-2 rounded-lg bg-red-800">Reject</button>
                  </div>
                </div>
              </div>;
            })}
            {approvals.length === 0 && <div className="text-zinc-500">No actions are awaiting approval.</div>}
          </div>
        </div>

        <div className="bg-zinc-900 border border-zinc-800 rounded-2xl p-6 mb-8">
          <h2 className="text-lg font-semibold mb-4">Agent Runs</h2>
          <div className="overflow-auto"><table className="w-full text-xs">
            <thead className="text-zinc-400"><tr className="border-b border-zinc-800">
              {["Request ID","Tenant","Agent","Originator","Steps","Provider","Policy","Status"].map(h => <th key={h} className="text-left py-3 pr-3">{h}</th>)}
            </tr></thead>
            <tbody>{agentRuns.map(run => <tr key={run.id} className="border-b border-zinc-900">
              <td className="py-3 pr-3">{run.requestId}</td><td className="pr-3">{run.tenantId}</td><td className="pr-3">{run.agentId}</td>
              <td className="pr-3">{run.originatingSubject}</td><td className="pr-3">{run.stepCount}/{run.maxSteps}</td>
              <td className="pr-3">{run.provider || "-"}</td><td className="pr-3">{run.policyResult || "-"}</td><td className="pr-3">{run.status}</td>
            </tr>)}</tbody>
          </table></div>
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
