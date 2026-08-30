"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useIdentityAuth } from "../auth-provider";

type UserRole = "INTERN" | "ENGINEER" | "FINANCE" | "ADMIN";
type AppUser = { email: string; role: UserRole; enabled: boolean; department?: string; clearance?: string; region?: string; policyAssignments?: string };
type AuditLog = { id:string; ts:string; requestId?:string; userEmail:string; role:UserRole; intent:string; confidence:number; allowed:boolean; decisionReason:string; piiTypes:string; provider:string; model?:string; routingReason?:string; latencyMs?:number; outputRedacted?:boolean };
type ProviderStatus = { provider:string; model:string; cloud:boolean; available:boolean; reason:string };
type AgentRun = { id:string; requestId:string; tenantId:string; agentId:string; originatingSubject:string; stepCount:number; maxSteps:number; provider?:string; policyResult?:string; status:string };
type Approval = { id:string; requestId:string; toolName:string; sanitizedArguments:string; reason:string; risk:string; status:string; decidedBy?:string; decidedAt?:string };
type McpProvider = { id:string; name:string; category:string; description:string; requiredScope:string; tenantEnabled:boolean; userEnabled:boolean; connectionStatus:string };
type AuditMetrics = { period:string; total:number; allowed:number; denied:number; piiHandled:number; outputRedactions:number; piiByType:Record<string,number>; trend:Array<{bucket:string;allowed:number;denied:number}> };
type Section = "dashboard" | "logs";
type Period = "24h" | "7d" | "30d";

const roles: UserRole[] = ["INTERN", "ENGINEER", "FINANCE", "ADMIN"];
const jsonExample = `{
  "users": [
    {
      "email": "engineer@company.com",
      "role": "ENGINEER",
      "enabled": true,
      "attributes": {
        "department": "engineering",
        "clearance": "INTERNAL",
        "region": "IN"
      },
      "policies": ["github.read", "mcp.github"]
    }
  ]
}`;

function Icon({ name, className = "w-5 h-5" }: { name:string; className?:string }) {
  const paths: Record<string, React.ReactNode> = {
    dashboard: <><rect x="3" y="3" width="7" height="7" rx="2"/><rect x="14" y="3" width="7" height="7" rx="2"/><rect x="3" y="14" width="7" height="7" rx="2"/><rect x="14" y="14" width="7" height="7" rx="2"/></>,
    logs: <><path d="M4 5h16M4 12h16M4 19h10"/><circle cx="19" cy="19" r="2"/></>,
    shield: <path d="M12 3 4.5 6v5.5c0 4.8 3 8 7.5 9.5 4.5-1.5 7.5-4.7 7.5-9.5V6L12 3Z"/>,
    refresh: <><path d="M20 7v5h-5"/><path d="M19 12a7 7 0 1 1-2-5"/></>,
    users: <><circle cx="9" cy="8" r="3"/><path d="M3 20c0-4 2.5-6 6-6s6 2 6 6"/><path d="M16 5.5a3 3 0 0 1 0 5.5M17 14c2.5.5 4 2.5 4 5"/></>,
    model: <><rect x="4" y="4" width="16" height="16" rx="4"/><path d="M9 9h6v6H9zM9 1v3M15 1v3M9 20v3M15 20v3M1 9h3M20 9h3M1 15h3M20 15h3"/></>,
    agent: <><circle cx="12" cy="8" r="4"/><path d="M5 21v-2a7 7 0 0 1 14 0v2M12 2V1M5 8H3M21 8h-2"/></>,
    plug: <><path d="m8 12 8-8M14 3l7 7M3 14l7 7M5 19l-2 2M19 5l2-2"/><path d="m8 9 7 7-2 2a5 5 0 0 1-7-7l2-2Z"/></>,
    arrow: <path d="m9 18 6-6-6-6"/>,
    upload: <><path d="M12 16V4M7 9l5-5 5 5"/><path d="M4 20h16"/></>,
  };
  return <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden>{paths[name]}</svg>;
}

function approvalTarget(approval: Approval) {
  try {
    const args = JSON.parse(approval.sanitizedArguments) as {repository?:string;repositoryUrl?:string;pullRequest?:number};
    const repository = args.repository || "imvaibhavm/ai-iam-gateway";
    const baseUrl = args.repositoryUrl || `https://github.com/${repository}`;
    return { repository, pullRequest:args.pullRequest, url:args.pullRequest ? `${baseUrl}/pull/${args.pullRequest}` : baseUrl };
  } catch { return { repository:"imvaibhavm/ai-iam-gateway", pullRequest:undefined, url:"https://github.com/imvaibhavm/ai-iam-gateway" }; }
}

function StatusBadge({ ok, children }: {ok:boolean;children:React.ReactNode}) {
  return <span className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[11px] font-semibold tracking-wide ${ok ? "border-emerald-500/25 bg-emerald-500/10 text-emerald-300" : "border-zinc-700 bg-zinc-800 text-zinc-400"}`}><span className={`h-1.5 w-1.5 rounded-full ${ok ? "bg-emerald-400" : "bg-zinc-500"}`}/>{children}</span>;
}

function DecisionTrend({ metrics }: {metrics:AuditMetrics|null}) {
  const points = metrics?.trend ?? [];
  const max = Math.max(1, ...points.map(point => point.allowed + point.denied));
  return <div className="h-44 flex items-end gap-1.5 pt-5" aria-label="Allow and deny activity trend">
    {points.map((point, index) => <div key={point.bucket} className="group relative flex-1 h-full flex flex-col justify-end min-w-[3px]">
      <div className="absolute hidden group-hover:block bottom-full mb-2 left-1/2 -translate-x-1/2 z-10 rounded-lg border border-zinc-700 bg-zinc-950 px-2 py-1 text-[10px] whitespace-nowrap">{point.allowed} allowed · {point.denied} denied</div>
      <div className="w-full rounded-t-sm bg-rose-400/80" style={{height:`${(point.denied/max)*100}%`, minHeight:point.denied ? 3 : 0}}/>
      <div className="w-full rounded-t-sm bg-cyan-400/80" style={{height:`${(point.allowed/max)*100}%`, minHeight:point.allowed ? 3 : 0}}/>
      {index % Math.max(1, Math.floor(points.length/5)) === 0 && <span className="absolute top-full mt-2 text-[9px] text-zinc-600">{index+1}</span>}
    </div>)}
    {!points.length && <div className="m-auto text-sm text-zinc-500">No activity in this period</div>}
  </div>;
}

function PiiBars({ data }: {data:Record<string,number>}) {
  const entries = Object.entries(data).sort((a,b) => b[1]-a[1]).slice(0,6);
  const max = Math.max(1, ...entries.map(([,value]) => value));
  return <div className="space-y-4 mt-5">{entries.map(([type,value]) => <div key={type}>
    <div className="mb-1.5 flex justify-between text-xs"><span className="text-zinc-300">{type.replaceAll("_", " ")}</span><span className="text-zinc-500">{value}</span></div>
    <div className="h-1.5 rounded-full bg-zinc-800"><div className="h-full rounded-full bg-gradient-to-r from-violet-500 to-cyan-400" style={{width:`${Math.max(5,(value/max)*100)}%`}}/></div>
  </div>)}{!entries.length && <div className="py-12 text-center text-sm text-zinc-500">No PII findings in this period</div>}</div>;
}

export default function AdminPage() {
  const router = useRouter();
  const auth = useIdentityAuth();
  const fileRef = useRef<HTMLInputElement>(null);
  const [section, setSection] = useState<Section>("dashboard");
  const [period, setPeriod] = useState<Period>("24h");
  const [users,setUsers] = useState<AppUser[]>([]); const [audits,setAudits] = useState<AuditLog[]>([]); const [providers,setProviders] = useState<ProviderStatus[]>([]);
  const [agentRuns,setAgentRuns] = useState<AgentRun[]>([]); const [approvals,setApprovals] = useState<Approval[]>([]); const [approvalHistory,setApprovalHistory] = useState<Approval[]>([]);
  const [mcpProviders,setMcpProviders] = useState<McpProvider[]>([]); const [metrics,setMetrics] = useState<AuditMetrics|null>(null);
  const [providerExpanded,setProviderExpanded] = useState(false); const [formatExpanded,setFormatExpanded] = useState(false); const [loading,setLoading] = useState(true); const [err,setErr] = useState("");
  const [newEmail,setNewEmail] = useState(""); const [newRole,setNewRole] = useState<UserRole>("INTERN");

  const ensureSession = useCallback(() => {
    if (auth.oidc) return true;
    if (typeof window !== "undefined" && localStorage.getItem("aiguard_user_email")) return true;
    router.push("/login"); return false;
  }, [auth.oidc, router]);

  const loadAll = useCallback(async () => {
    if (!ensureSession()) return;
    setLoading(true); setErr("");
    try {
      const meResponse = await auth.apiFetch("/user/me");
      if (!meResponse.ok || (await meResponse.json()).role !== "ADMIN") { router.push("/"); return; }
      const responses = await Promise.all([
        auth.apiFetch("/admin/users"), auth.apiFetch("/admin/audit?limit=100"), auth.apiFetch("/admin/providers"),
        auth.apiFetch("/agent/runs"), auth.apiFetch("/admin/approvals"), auth.apiFetch("/admin/approvals/history"),
        auth.apiFetch("/admin/mcp/providers"), auth.apiFetch(`/admin/audit/metrics?period=${period}`),
      ]);
      if (responses.some(response => !response.ok)) throw new Error("Some workspace data could not be loaded. Refresh to retry.");
      const [usersData,auditsData,providersData,runsData,approvalsData,historyData,mcpData,metricsData] = await Promise.all(responses.map(response => response.json()));
      setUsers(usersData); setAudits(auditsData); setProviders(providersData); setAgentRuns(runsData); setApprovals(approvalsData); setApprovalHistory(historyData); setMcpProviders(mcpData); setMetrics(metricsData);
    } catch (cause) { setErr(cause instanceof Error ? cause.message : "Workspace data could not be loaded"); }
    finally { setLoading(false); }
  }, [auth, ensureSession, period, router]);

  useEffect(() => { void loadAll(); }, [loadAll]);

  const connected = providers.filter(provider => provider.available).length;
  const providerTotal = providers.length || 7;
  const connectedPercent = Math.round((connected/providerTotal)*100);
  const coverage = useMemo(() => ({ approved:mcpProviders.filter(item => item.tenantEnabled).length, total:mcpProviders.length }), [mcpProviders]);

  async function addUser() {
    if (!newEmail.trim()) return;
    const response = await auth.apiFetch("/admin/users", {method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({email:newEmail.trim().toLowerCase(),role:newRole,enabled:true})});
    if (!response.ok) { setErr("User could not be added"); return; }
    setNewEmail(""); setNewRole("INTERN"); await loadAll();
  }
  async function importUsers(file?:File) {
    if (!file) return;
    try {
      const parsed = JSON.parse(await file.text());
      const response = await auth.apiFetch("/admin/users/import", {method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(parsed)});
      if (!response.ok) throw new Error(await response.text() || "Import rejected");
      const result = await response.json(); setErr(""); alert(`${result.imported} users added or updated.`); await loadAll();
    } catch (cause) { setErr(cause instanceof Error ? cause.message : "The JSON file is invalid"); }
    finally { if (fileRef.current) fileRef.current.value = ""; }
  }
  async function updateRole(email:string, role:UserRole) { await auth.apiFetch(`/admin/users/${encodeURIComponent(email)}/role/${role}`,{method:"PUT"}); await loadAll(); }
  async function updateEnabled(email:string, enabled:boolean) { await auth.apiFetch(`/admin/users/${encodeURIComponent(email)}/enabled/${enabled}`,{method:"PUT"}); await loadAll(); }
  async function decideApproval(id:string, decision:"approve"|"reject") { const response=await auth.apiFetch(`/admin/approvals/${id}/${decision}`,{method:"POST"}); if(!response.ok)setErr(`The action could not be ${decision}d safely.`); await loadAll(); }
  async function toggleMcp(item:McpProvider) { const response=await auth.apiFetch(`/admin/mcp/providers/${item.id}/enabled/${!item.tenantEnabled}`,{method:"PUT"}); if(!response.ok)setErr("Connection policy could not be updated"); await loadAll(); }
  function logout() { localStorage.removeItem("aiguard_user_email"); localStorage.removeItem("aiguard_access_token"); if(auth.oidc)auth.logout(); else router.push("/login"); }

  return <div className="min-h-screen bg-[#07090d] text-zinc-100 selection:bg-cyan-400/20">
    <aside className="fixed inset-y-0 left-0 z-20 hidden w-64 border-r border-white/[0.07] bg-[#0a0d12] md:flex md:flex-col">
      <div className="h-20 flex items-center gap-3 border-b border-white/[0.07] px-6"><div className="h-9 w-9 rounded-xl bg-gradient-to-br from-cyan-300 to-emerald-400 text-zinc-950 flex items-center justify-center"><Icon name="shield"/></div><div><div className="font-semibold tracking-tight">Friday</div><div className="text-[10px] uppercase tracking-[.2em] text-zinc-500">Control plane</div></div></div>
      <nav className="p-3 space-y-1" aria-label="Admin navigation">
        {([['dashboard','Dashboard','Security posture and controls'],['logs','Logs','Request decision evidence']] as const).map(([id,label,caption]) => <button key={id} onClick={()=>setSection(id)} className={`w-full flex items-center gap-3 rounded-xl px-3 py-3 text-left transition ${section===id?'bg-white/[0.08] text-white shadow-inner shadow-white/[0.03]':'text-zinc-500 hover:bg-white/[0.04] hover:text-zinc-200'}`}><Icon name={id}/><span><span className="block text-sm font-medium">{label}</span><span className="block text-[10px] mt-0.5 text-zinc-600">{caption}</span></span></button>)}
      </nav>
      <div className="mt-auto p-4"><button onClick={()=>router.push('/')} className="w-full rounded-xl border border-white/[0.08] px-4 py-2.5 text-sm text-zinc-400 hover:text-white">Return to workspace</button></div>
    </aside>

    <div className="md:pl-64">
      <header className="sticky top-0 z-10 h-20 border-b border-white/[0.07] bg-[#07090d]/90 px-4 backdrop-blur-xl sm:px-8 flex items-center justify-between">
        <div><h1 className="text-xl font-semibold tracking-tight">{section === 'dashboard' ? 'Security dashboard' : 'Decision logs'}</h1><p className="text-xs text-zinc-500 mt-1">{section === 'dashboard' ? 'AI activity, access controls and connected systems' : 'Evidence for every policy decision and model request'}</p></div>
        <div className="flex items-center gap-2"><button onClick={()=>void loadAll()} className="h-10 w-10 rounded-xl border border-white/[0.08] text-zinc-400 hover:text-white flex items-center justify-center" aria-label="Refresh"><Icon name="refresh" className={`w-4 h-4 ${loading?'animate-spin':''}`}/></button><button onClick={logout} className="rounded-xl bg-zinc-100 px-4 py-2.5 text-sm font-medium text-zinc-950">Sign out</button></div>
      </header>
      <div className="md:hidden px-4 pt-4 flex gap-2">{(['dashboard','logs'] as Section[]).map(id=><button key={id} onClick={()=>setSection(id)} className={`flex-1 rounded-xl px-3 py-2.5 text-sm capitalize ${section===id?'bg-white text-black':'bg-zinc-900 text-zinc-400'}`}>{id}</button>)}</div>
      <main className="mx-auto max-w-[1500px] p-4 sm:p-8">
        {err && <div className="mb-6 rounded-xl border border-rose-500/20 bg-rose-500/[0.08] px-4 py-3 text-sm text-rose-300">{err}</div>}
        {section === 'dashboard' ? <>
          <section className="mb-8"><div className="flex flex-wrap items-end justify-between gap-4 mb-5"><div><p className="text-[11px] font-semibold uppercase tracking-[.18em] text-cyan-400">Security posture</p><h2 className="mt-2 text-2xl font-semibold tracking-tight">What happened across your AI estate</h2></div><div className="flex rounded-xl border border-white/[0.08] bg-zinc-950 p-1">{(['24h','7d','30d'] as Period[]).map(value=><button key={value} onClick={()=>setPeriod(value)} className={`rounded-lg px-3 py-1.5 text-xs ${period===value?'bg-zinc-800 text-white':'text-zinc-500'}`}>{value==='24h'?'24 hours':value==='7d'?'7 days':'30 days'}</button>)}</div></div>
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">{[
              ['Requests reviewed',metrics?.total??0,'All protected AI requests','text-cyan-300'],['Allowed',metrics?.allowed??0,'Passed current policy','text-emerald-300'],['Denied',metrics?.denied??0,'Stopped before inference','text-rose-300'],['PII protected',metrics?.piiHandled??0,'Requests masked or controlled','text-violet-300']
            ].map(([label,value,caption,color])=><div key={String(label)} className="rounded-2xl border border-white/[0.07] bg-white/[0.025] p-5"><div className="flex items-start justify-between"><span className="text-sm text-zinc-400">{label}</span><span className={`text-lg ${color}`}>↗</span></div><div className="mt-5 text-3xl font-semibold tracking-tight">{value}</div><div className="mt-2 text-xs text-zinc-600">{caption}</div></div>)}</div>
          </section>

          <section className="grid gap-5 xl:grid-cols-[1.35fr_.85fr] mb-8">
            <div className="rounded-2xl border border-white/[0.07] bg-white/[0.025] p-6"><div className="flex items-center justify-between"><div><h3 className="font-semibold">Policy decisions</h3><p className="text-xs text-zinc-500 mt-1">Allow and deny activity over the selected period</p></div><div className="flex gap-4 text-[11px]"><span className="text-cyan-300">● Allowed</span><span className="text-rose-300">● Denied</span></div></div><DecisionTrend metrics={metrics}/></div>
            <div className="rounded-2xl border border-white/[0.07] bg-white/[0.025] p-6"><div className="flex items-center justify-between"><div><h3 className="font-semibold">Sensitive data handled</h3><p className="text-xs text-zinc-500 mt-1">Detected types, after de-duplication per request</p></div><span className="text-2xl font-semibold text-violet-300">{metrics?.piiHandled??0}</span></div><PiiBars data={metrics?.piiByType??{}}/></div>
          </section>

          <section className="grid gap-5 xl:grid-cols-2 mb-8">
            <div className="rounded-2xl border border-white/[0.07] bg-white/[0.025] overflow-hidden"><div className="p-6 flex items-center justify-between"><div><div className="flex items-center gap-2 text-sm font-semibold"><Icon name="model" className="w-4 h-4 text-cyan-300"/>Model connections</div><p className="text-xs text-zinc-500 mt-1">Provider availability after configuration and health checks</p></div><StatusBadge ok={connected>0}>{connected} connected</StatusBadge></div>
              <div className="px-6 pb-6 flex items-center gap-8"><div className="relative h-36 w-36 shrink-0 rounded-full" style={{background:`conic-gradient(#22d3ee 0 ${connectedPercent}%, #27272a ${connectedPercent}% 100%)`}}><div className="absolute inset-3 rounded-full bg-[#0b0e13] flex flex-col items-center justify-center"><span className="text-3xl font-semibold">{providerTotal}</span><span className="text-[10px] text-zinc-500 uppercase tracking-wider">total</span></div></div><div className="space-y-4 flex-1"><div><div className="text-2xl font-semibold text-cyan-300">{connected}</div><div className="text-xs text-zinc-500">Connected and eligible</div></div><div><div className="text-2xl font-semibold text-zinc-300">{Math.max(0,providerTotal-connected)}</div><div className="text-xs text-zinc-500">Available integrations</div></div></div></div>
              {providerExpanded && <div className="border-t border-white/[0.07] p-4 grid gap-2 sm:grid-cols-2">{providers.map(provider=><div key={provider.provider} className="rounded-xl border border-white/[0.06] bg-black/20 p-3"><div className="flex justify-between gap-3"><span className="text-sm font-medium capitalize">{provider.provider}</span><StatusBadge ok={provider.available}>{provider.available?'Connected':'Not connected'}</StatusBadge></div><div className="mt-2 text-[11px] text-zinc-500 truncate">{provider.model}</div><div className="mt-1 text-[10px] text-zinc-600">{provider.cloud?'Hosted model':'Private / local'} · {provider.reason}</div></div>)}</div>}
              <button onClick={()=>setProviderExpanded(value=>!value)} className="w-full border-t border-white/[0.07] px-6 py-3 text-right text-xs text-cyan-300 hover:bg-white/[0.025]">{providerExpanded?'Collapse details':'Expand provider details'} →</button>
            </div>
            <div className="rounded-2xl border border-white/[0.07] bg-white/[0.025] p-6"><div className="flex items-start justify-between"><div><div className="flex items-center gap-2 text-sm font-semibold"><Icon name="agent" className="w-4 h-4 text-amber-300"/>Agent activity</div><p className="text-xs text-zinc-500 mt-1">Recent autonomous workflows and policy state</p></div><span className="text-2xl font-semibold">{agentRuns.length}</span></div><div className="mt-5 space-y-2">{agentRuns.slice(0,5).map(run=><div key={run.id} className="flex items-center justify-between gap-4 rounded-xl border border-white/[0.06] bg-black/20 p-3"><div className="min-w-0"><div className="truncate text-sm">{run.agentId}</div><div className="mt-1 truncate font-mono text-[10px] text-zinc-600">{run.requestId}</div></div><div className="text-right"><div className="text-xs">{run.stepCount}/{run.maxSteps} steps</div><div className="mt-1 text-[10px] text-zinc-500">{run.status.replaceAll('_',' ')}</div></div></div>)}{!agentRuns.length&&<div className="py-14 text-center text-sm text-zinc-500">No agent activity yet</div>}</div></div>
          </section>

          <section className="rounded-2xl border border-white/[0.07] bg-white/[0.025] mb-8 overflow-hidden"><div className="p-6 flex flex-wrap items-start justify-between gap-4"><div><div className="flex items-center gap-2 text-sm font-semibold"><Icon name="plug" className="w-4 h-4 text-emerald-300"/>MCP connections</div><p className="text-xs text-zinc-500 mt-1">Choose which tool connections people in this workspace may enable</p></div><div className="text-right"><div className="text-xl font-semibold">{coverage.approved}/{coverage.total}</div><div className="text-[10px] text-zinc-600">approved for workspace</div></div></div><div className="grid gap-3 border-t border-white/[0.07] p-6 md:grid-cols-2 xl:grid-cols-3">{mcpProviders.map(item=><div key={item.id} className="rounded-xl border border-white/[0.07] bg-black/20 p-4"><div className="flex items-start justify-between gap-3"><div><div className="font-medium">{item.name}</div><div className="text-[10px] text-emerald-400/70 mt-1">{item.category} · {item.requiredScope}</div></div><button onClick={()=>void toggleMcp(item)} className={`relative h-6 w-11 rounded-full transition ${item.tenantEnabled?'bg-emerald-400':'bg-zinc-700'}`} aria-label={`${item.tenantEnabled?'Disable':'Enable'} ${item.name}`}><span className={`absolute top-1 h-4 w-4 rounded-full bg-white transition ${item.tenantEnabled?'left-6':'left-1'}`}/></button></div><p className="mt-3 text-xs leading-relaxed text-zinc-500">{item.description}</p><p className="mt-3 text-[10px] text-zinc-600">Catalog integration · external credentials are configured separately</p></div>)}</div></section>

          <section className="grid gap-5 xl:grid-cols-2 mb-8">
            <div className="rounded-2xl border border-amber-400/15 bg-amber-400/[0.025] p-6"><div className="flex justify-between"><div><h3 className="font-semibold">Actions awaiting review</h3><p className="text-xs text-zinc-500 mt-1">High-impact agent actions pause here before execution</p></div><span className="h-8 min-w-8 rounded-full bg-amber-400/10 text-amber-300 flex items-center justify-center text-sm">{approvals.length}</span></div><div className="mt-5 space-y-3">{approvals.map(approval=>{const target=approvalTarget(approval);return <div key={approval.id} className="rounded-xl border border-amber-400/15 bg-black/20 p-4"><div className="flex flex-wrap justify-between gap-3"><div><div className="text-sm font-medium">{approval.toolName.replaceAll('.',' › ')}</div><a href={target.url} target="_blank" rel="noreferrer" className="text-xs text-cyan-300 hover:underline">{target.repository}{target.pullRequest?` · change #${target.pullRequest}`:''}</a><div className="mt-2 text-[10px] text-zinc-600">{approval.risk} impact · request {approval.requestId}</div></div><div className="flex gap-2"><button onClick={()=>void decideApproval(approval.id,'reject')} className="rounded-lg border border-white/[0.08] px-3 py-2 text-xs text-zinc-300">Reject</button><button onClick={()=>void decideApproval(approval.id,'approve')} className="rounded-lg bg-emerald-400 px-3 py-2 text-xs font-semibold text-zinc-950">Approve</button></div></div></div>})}{!approvals.length&&<div className="py-12 text-center text-sm text-zinc-500">No actions need review</div>}</div></div>
            <div className="rounded-2xl border border-white/[0.07] bg-white/[0.025] p-6"><h3 className="font-semibold">Code change decisions</h3><p className="text-xs text-zinc-500 mt-1">Recent protected repository actions</p><div className="mt-5 space-y-2">{approvalHistory.slice(0,6).map(item=>{const target=approvalTarget(item);return <div key={item.id} className="flex items-center justify-between gap-3 rounded-xl border border-white/[0.06] p-3"><div className="min-w-0"><a href={target.url} target="_blank" rel="noreferrer" className="text-sm text-cyan-300 hover:underline truncate block">{target.repository}{target.pullRequest?` · #${target.pullRequest}`:''}</a><div className="text-[10px] text-zinc-600 mt-1">Reviewed by {item.decidedBy||'policy administrator'}</div></div><StatusBadge ok={item.status==='APPROVED'}>{item.status.toLowerCase()}</StatusBadge></div>})}{!approvalHistory.length&&<div className="py-12 text-center text-sm text-zinc-500">No code change decisions recorded</div>}</div></div>
          </section>

          <section className="rounded-2xl border border-white/[0.07] bg-white/[0.025] overflow-hidden"><div className="p-6 border-b border-white/[0.07]"><div className="flex items-center gap-2 text-sm font-semibold"><Icon name="users" className="w-4 h-4 text-violet-300"/>People and access</div><p className="text-xs text-zinc-500 mt-1">Add people individually or import a reviewed tenant-safe JSON file</p></div><div className="p-6 grid gap-3 lg:grid-cols-[1fr_180px_auto_auto]"><input value={newEmail} onChange={event=>setNewEmail(event.target.value)} placeholder="person@company.com" className="rounded-xl border border-white/[0.08] bg-black/20 px-4 py-3 text-sm outline-none focus:border-cyan-400/40"/><select value={newRole} onChange={event=>setNewRole(event.target.value as UserRole)} className="rounded-xl border border-white/[0.08] bg-[#0b0e13] px-4 py-3 text-sm">{roles.map(role=><option key={role}>{role}</option>)}</select><button onClick={()=>void addUser()} className="rounded-xl bg-zinc-100 px-5 py-3 text-sm font-semibold text-zinc-950">Add user</button><button onClick={()=>fileRef.current?.click()} className="rounded-xl border border-white/[0.1] px-4 py-3 text-sm text-zinc-300 flex items-center justify-center gap-2"><Icon name="upload" className="w-4 h-4"/>Upload JSON</button><input ref={fileRef} type="file" accept="application/json,.json" className="hidden" onChange={event=>void importUsers(event.target.files?.[0])}/></div>
            <div className="px-6 pb-4"><button onClick={()=>setFormatExpanded(value=>!value)} className="text-xs text-cyan-300">{formatExpanded?'Hide':'View'} JSON format and policy fields</button>{formatExpanded&&<div className="mt-3 grid gap-4 rounded-xl border border-white/[0.07] bg-black/30 p-4 lg:grid-cols-[1fr_1.2fr]"><div className="text-xs leading-6 text-zinc-500"><p>The server assigns every imported person to the current administrator&apos;s tenant. Supported roles are INTERN, ENGINEER, FINANCE and ADMIN.</p><p className="mt-2">Attributes become server-authoritative ABAC inputs. Policy entries are stored as explicit assignments and cannot grant access outside platform policy.</p></div><pre className="overflow-auto rounded-lg bg-black/40 p-3 text-[10px] leading-5 text-zinc-400">{jsonExample}</pre></div>}</div>
            <div className="overflow-auto border-t border-white/[0.07]"><table className="w-full text-left text-xs"><thead className="text-zinc-600"><tr>{['Person','Role','Department','Policy assignments','Access'].map(label=><th key={label} className="px-6 py-3 font-medium">{label}</th>)}</tr></thead><tbody>{users.map(user=><tr key={user.email} className="border-t border-white/[0.05]"><td className="px-6 py-4"><div className="text-zinc-200">{user.email}</div><div className="text-[10px] text-zinc-600 mt-1">{user.clearance||'Default clearance'} · {user.region||'Any region'}</div></td><td className="px-6 py-4"><select value={user.role} onChange={event=>void updateRole(user.email,event.target.value as UserRole)} className="rounded-lg border border-white/[0.08] bg-[#0b0e13] px-2 py-1.5">{roles.map(role=><option key={role}>{role}</option>)}</select></td><td className="px-6 py-4 text-zinc-400">{user.department||'—'}</td><td className="px-6 py-4 text-zinc-500 max-w-xs truncate">{user.policyAssignments||'Platform defaults'}</td><td className="px-6 py-4"><button onClick={()=>void updateEnabled(user.email,!user.enabled)}><StatusBadge ok={user.enabled}>{user.enabled?'Enabled':'Disabled'}</StatusBadge></button></td></tr>)}</tbody></table></div>
          </section>
        </> : <section className="rounded-2xl border border-white/[0.07] bg-white/[0.025] overflow-hidden">
          <div className="p-6 flex flex-wrap items-end justify-between gap-4 border-b border-white/[0.07]"><div><p className="text-[11px] font-semibold uppercase tracking-[.18em] text-cyan-400">Authoritative evidence</p><h2 className="mt-2 text-xl font-semibold">Request decision logs</h2><p className="mt-1 text-xs text-zinc-500">The latest 100 records for this tenant. No raw prompts or credentials are stored.</p></div><div className="flex gap-2"><span className="rounded-lg border border-white/[0.08] px-3 py-2 text-xs text-zinc-500">{audits.length} records</span><button onClick={()=>void loadAll()} className="rounded-lg bg-zinc-100 px-3 py-2 text-xs font-medium text-zinc-950">Refresh logs</button></div></div>
          <div className="overflow-auto"><table className="w-full min-w-[1200px] text-left text-xs"><thead className="bg-black/20 text-zinc-600"><tr>{['Time','Identity','Role','Intent','Confidence','Decision','Reason','PII handled','Provider / model','Latency'].map(label=><th key={label} className="px-4 py-3 font-medium">{label}</th>)}</tr></thead><tbody>{audits.map(log=><tr key={log.id} className="border-t border-white/[0.05] hover:bg-white/[0.02]"><td className="px-4 py-4 text-zinc-500 whitespace-nowrap">{log.ts?new Date(log.ts).toLocaleString():'—'}</td><td className="px-4 py-4"><div>{log.userEmail}</div><div className="mt-1 font-mono text-[9px] text-zinc-700">{log.requestId||log.id}</div></td><td className="px-4 py-4 text-zinc-400">{log.role}</td><td className="px-4 py-4">{log.intent}</td><td className="px-4 py-4 font-mono">{Number(log.confidence??0).toFixed(2)}</td><td className="px-4 py-4"><StatusBadge ok={log.allowed}>{log.allowed?'Allowed':'Denied'}</StatusBadge></td><td className="px-4 py-4 text-zinc-400 max-w-[220px]">{log.decisionReason}</td><td className="px-4 py-4 text-violet-300">{log.piiTypes||'—'}</td><td className="px-4 py-4"><div className="capitalize">{log.provider||'—'}</div><div className="mt-1 text-[10px] text-zinc-600">{log.model||log.routingReason||''}</div></td><td className="px-4 py-4 text-zinc-500">{log.latencyMs?`${log.latencyMs} ms`:'—'}</td></tr>)}{!audits.length&&!loading&&<tr><td colSpan={10} className="py-20 text-center text-zinc-500">No request logs are available yet.</td></tr>}</tbody></table></div>
        </section>}
      </main>
    </div>
  </div>;
}
