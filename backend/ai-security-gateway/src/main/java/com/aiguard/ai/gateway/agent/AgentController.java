package com.aiguard.ai.gateway.agent;

import com.aiguard.ai.gateway.identity.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import com.aiguard.ai.gateway.mcp.McpCatalogService;

@RestController @RequestMapping("/api/agent")
public class AgentController {
    private final AgentExecutionService service; private final IdentityResolver identities; private final McpCatalogService mcpCatalog;
    public AgentController(AgentExecutionService service, IdentityResolver identities, McpCatalogService mcpCatalog) { this.service=service;this.identities=identities;this.mcpCatalog=mcpCatalog; }
    @PostMapping("/runs") public AgentRun create(@RequestBody StartRequest request, Authentication auth) {
        IdentityContext identity = identities.require(auth);
        mcpCatalog.requireUserEnabled(identity, request.requestedTools());
        return service.create(identity,request.prompt(),request.agentId(),request.maxSteps());
    }
    @GetMapping("/runs") public List<AgentRun> list(Authentication auth) { return service.list(identities.require(auth)); }
    @GetMapping("/runs/{id}") public AgentRun get(@PathVariable String id, Authentication auth) {
        AgentRun run=service.require(id); if(!run.getTenantId().equals(identities.require(auth).tenantId()))throw new SecurityException("cross_tenant_run"); return run;
    }
    public record StartRequest(String prompt,String agentId,int maxSteps,List<String> requestedTools) {}
}
