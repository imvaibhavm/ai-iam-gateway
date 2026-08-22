package com.aiguard.ai.gateway.agent;

import com.aiguard.ai.gateway.identity.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/admin/approvals")
public class ApprovalController {
    private final AgentExecutionService service; private final IdentityResolver identities;
    public ApprovalController(AgentExecutionService service, IdentityResolver identities){this.service=service;this.identities=identities;}
    @GetMapping public List<AgentApproval> pending(Authentication auth){return service.pending(identities.require(auth));}
    @PostMapping("/{id}/approve") public AgentApproval approve(@PathVariable String id,Authentication auth){return service.decide(id,true,identities.require(auth));}
    @PostMapping("/{id}/reject") public AgentApproval reject(@PathVariable String id,Authentication auth){return service.decide(id,false,identities.require(auth));}
}
