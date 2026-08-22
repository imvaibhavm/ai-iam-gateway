package com.aiguard.ai.gateway.audit.controller;

import com.aiguard.ai.gateway.audit.entity.AuditLog;
import com.aiguard.ai.gateway.audit.repo.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.aiguard.ai.gateway.identity.IdentityResolver;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogRepository repo;
    private final IdentityResolver identityResolver;

    /**
     * Latest audit logs (default last 100)
     */
    @GetMapping
    public List<AuditLog> latest(@RequestParam(defaultValue = "100") int limit, Authentication auth) {

        int safeLimit = Math.min(Math.max(limit, 1), 500);

        return repo.findByTenantId(identityResolver.require(auth).tenantId(), PageRequest.of(
                        0,
                        safeLimit,
                        Sort.by(Sort.Direction.DESC, "ts")
                ));
    }
}
