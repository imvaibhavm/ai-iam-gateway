package com.aiguard.ai.gateway.agent;

import com.aiguard.ai.gateway.chat.service.ChatService;
import com.aiguard.ai.gateway.audit.repo.AuditLogRepository;
import com.aiguard.ai.gateway.identity.*;
import com.aiguard.ai.gateway.iam.UserRole;
import com.aiguard.ai.gateway.observability.AiTelemetry;
import com.aiguard.ai.gateway.tool.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
public class AgentExecutionService {
    private final AgentRunRepository runs; private final AgentApprovalRepository approvals;
    private final AgentRuntimeClient runtime; private final ChatService chat; private final ToolGateway tools;
    private final ObjectMapper json; private final AiTelemetry telemetry;
    private final AuditLogRepository auditLogs;
    public AgentExecutionService(AgentRunRepository runs, AgentApprovalRepository approvals,
            AgentRuntimeClient runtime, ChatService chat, ToolGateway tools, ObjectMapper json, AiTelemetry telemetry,
            AuditLogRepository auditLogs) {
        this.runs=runs; this.approvals=approvals; this.runtime=runtime; this.chat=chat; this.tools=tools;
        this.json=json; this.telemetry=telemetry; this.auditLogs=auditLogs;
    }

    public AgentRun create(IdentityContext origin, String prompt, String requestedAgentId, int requestedMaxSteps) {
        String id=UUID.randomUUID().toString(), requestId=UUID.randomUUID().toString();
        Set<String> scopes = permittedAgentScopes(origin);
        AgentRun run=AgentRun.builder().id(id).requestId(requestId).tenantId(origin.tenantId())
                .agentId(safeAgentId(requestedAgentId)).originatingSubject(origin.subject()).email(origin.email())
                .role(origin.role()).identityType(IdentityType.AGENT).delegationChain(origin.subject())
                .effectiveScopes(String.join(",", scopes)).prompt(prompt).stepCount(0)
                .maxSteps(Math.max(1, Math.min(requestedMaxSteps <= 0 ? 8 : requestedMaxSteps, 20)))
                .status(AgentRunStatus.CREATED).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        // Commit the security context before the out-of-process runtime can call back for it.
        runs.saveAndFlush(run);
        try { var result=runtime.start(id); run=require(id); applyRuntimeResult(run, result); syncInferenceMetadata(run); }
        catch (RuntimeException error) { run.setStatus(AgentRunStatus.FAILED); run.setResponse("Agent runtime unavailable"); }
        run.setUpdatedAt(Instant.now()); return runs.save(run);
    }

    public AgentRun require(String runId) { return runs.findById(runId).orElseThrow(() -> new NoSuchElementException("agent_run_not_found")); }
    public List<AgentRun> list(IdentityContext identity) { return runs.findTop100ByTenantIdOrderByCreatedAtDesc(identity.tenantId()); }
    public List<AgentApproval> pending(IdentityContext identity) {
        return approvals.findByTenantIdAndStatusOrderByCreatedAtDesc(identity.tenantId(), ApprovalStatus.PENDING);
    }
    public List<AgentApproval> approvalHistory(IdentityContext identity) {
        return approvals.findTop100ByTenantIdOrderByCreatedAtDesc(identity.tenantId());
    }

    public AgentApproval decide(String approvalId, boolean approved, IdentityContext decider) {
        AgentApproval approval=approvals.findById(approvalId).orElseThrow(() -> new NoSuchElementException("approval_not_found"));
        if (!approval.getTenantId().equals(decider.tenantId())) throw new SecurityException("cross_tenant_approval");
        if (decider.role()!=UserRole.ADMIN) throw new SecurityException("approval_requires_admin");
        if (approval.getStatus()!=ApprovalStatus.PENDING) throw new IllegalStateException("approval_already_decided");
        approval.setStatus(approved ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        approval.setDecidedBy(decider.subject()); approval.setDecidedAt(Instant.now()); approvals.saveAndFlush(approval);
        AgentRun run=require(approval.getRunId());
        try { var result=runtime.resume(run.getId(), approved); run=require(run.getId()); applyRuntimeResult(run, result); syncInferenceMetadata(run); }
        catch (RuntimeException error) { run.setStatus(AgentRunStatus.FAILED); run.setResponse("Agent resume failed safely"); }
        run.setUpdatedAt(Instant.now()); runs.save(run);
        return approval;
    }

    public Map<String,Object> context(String runId) {
        AgentRun run=require(runId);
        return Map.of("requestId",run.getRequestId(),"tenantId",run.getTenantId(),"agentId",run.getAgentId(),
                "originatingSubject",run.getOriginatingSubject(),"delegationChain",List.of(run.getDelegationChain()),
                "effectiveScopes",scopes(run),"prompt",run.getPrompt(),"stepCount",run.getStepCount(),"maxSteps",run.getMaxSteps());
    }

    @Transactional
    public String inference(String runId, String prompt) {
        AgentRun run=require(runId); consumeStep(run);
        return telemetry.observe("ai.agent.step", safe(run), () -> chat.reply(identity(run), prompt, run.getRequestId()));
    }

    @Transactional
    public Map<String,Object> propose(String runId, String toolName, Map<String,Object> arguments) {
        AgentRun run=require(runId); consumeStep(run);
        ToolRequest request=new ToolRequest(run.getRequestId(), identity(run), toolName, withTenant(run, arguments));
        return telemetry.observe("ai.tool.policy", Map.of("ai.request.id",run.getRequestId(),"ai.tenant.id",run.getTenantId(),
                "ai.tool.name",toolName), () -> {
            ToolGateway.Authorization decision=tools.authorize(request);
            if (!decision.allowed()) return Map.of("decision","DENY","reason",decision.reason());
            if (decision.approvalRequired()) {
                AgentApproval approval=AgentApproval.builder().id(UUID.randomUUID().toString()).runId(runId)
                        .requestId(run.getRequestId()).tenantId(run.getTenantId()).toolName(toolName)
                        .sanitizedArguments(write(arguments)).reason(decision.reason())
                        .risk(AgentApproval.ToolRiskSnapshot.valueOf(decision.descriptor().riskLevel().name()))
                        .status(ApprovalStatus.PENDING).createdAt(Instant.now()).build();
                approvals.save(approval); run.setStatus(AgentRunStatus.WAITING_APPROVAL); runs.save(run);
                return Map.of("decision","REQUIRE_APPROVAL","reason",decision.reason(),"approvalId",approval.getId());
            }
            ToolResult result=tools.execute(request);
            return Map.of("decision","ALLOW","result",result.content(),"redacted",result.resultRedacted());
        });
    }

    @Transactional
    public Map<String,Object> executeApproved(String approvalId) {
        AgentApproval approval=approvals.findById(approvalId).orElseThrow(() -> new NoSuchElementException("approval_not_found"));
        if (approval.getStatus()!=ApprovalStatus.APPROVED) throw new SecurityException("approval_not_approved");
        AgentRun run=require(approval.getRunId());
        Map<String,Object> args=read(approval.getSanitizedArguments());
        ToolRequest request=new ToolRequest(run.getRequestId(),identity(run),approval.getToolName(),withTenant(run,args));
        // ToolGateway re-runs tenant, scope, DLP, role and risk policy. Approval never overrides a new DENY.
        ToolResult result=tools.executeApproved(request);
        approval.setStatus(ApprovalStatus.EXECUTED); approvals.save(approval);
        return Map.of("decision","ALLOW","result",result.content(),"redacted",result.resultRedacted());
    }

    private void consumeStep(AgentRun run) {
        if (run.getStepCount() >= run.getMaxSteps()) { run.setStatus(AgentRunStatus.STEP_BUDGET_EXCEEDED); runs.save(run); throw new IllegalStateException("agent_step_budget_exceeded"); }
        run.setStepCount(run.getStepCount()+1); run.setStatus(AgentRunStatus.RUNNING); run.setUpdatedAt(Instant.now()); runs.save(run);
    }
    private IdentityContext identity(AgentRun run) { return new IdentityContext(run.getAgentId(),run.getEmail(),run.getTenantId(),run.getRole(),IdentityType.AGENT,run.getOriginatingSubject(),scopes(run),Map.of("originator",run.getOriginatingSubject())); }
    private Set<String> scopes(AgentRun run) { return run.getEffectiveScopes()==null||run.getEffectiveScopes().isBlank()?Set.of():Set.of(run.getEffectiveScopes().split(",")); }
    private Set<String> permittedAgentScopes(IdentityContext origin) { return origin.role()==UserRole.ADMIN?Set.of("llm.generate","github.read","github.write"):origin.role()==UserRole.ENGINEER?Set.of("llm.generate","github.read"):Set.of("llm.generate"); }
    private Map<String,Object> withTenant(AgentRun run, Map<String,Object> args) { Map<String,Object> copy=new HashMap<>(args==null?Map.of():args); copy.put("tenantId",run.getTenantId()); return copy; }
    private String safeAgentId(String value) { return value==null||value.isBlank()?"github-agent":value.replaceAll("[^a-zA-Z0-9._-]","_").substring(0,Math.min(value.length(),80)); }
    private Map<String,String> safe(AgentRun run) { return Map.of("ai.request.id",run.getRequestId(),"ai.tenant.id",run.getTenantId(),"ai.agent.id",run.getAgentId()); }
    private String write(Object value) { try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("arguments_not_serializable",e);} }
    private Map<String,Object> read(String value) { try{return json.readValue(value,new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException("approval_arguments_invalid",e);} }
    private void applyRuntimeResult(AgentRun run, Map<String,Object> result) {
        String status=String.valueOf(result.getOrDefault("status","FAILED"));
        run.setStatus(switch(status){case "WAITING_APPROVAL"->AgentRunStatus.WAITING_APPROVAL;case "COMPLETED"->AgentRunStatus.COMPLETED;case "REJECTED"->AgentRunStatus.REJECTED;case "STEP_BUDGET_EXCEEDED"->AgentRunStatus.STEP_BUDGET_EXCEEDED;default->AgentRunStatus.FAILED;});
        Object response=result.get("response"); if(response!=null)run.setResponse(String.valueOf(response));
    }
    private void syncInferenceMetadata(AgentRun run) {
        auditLogs.findTopByRequestIdOrderByTsDesc(run.getRequestId()).ifPresent(audit -> {
            run.setProvider(audit.getProvider()); run.setPolicyResult(audit.isAllowed() ? "ALLOW" : "DENY");
        });
    }
}
