package com.aiguard.ai.gateway.audit.controller;

import com.aiguard.ai.gateway.audit.entity.AuditLog;
import com.aiguard.ai.gateway.audit.repo.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogRepository repo;

    /**
     * Latest audit logs (default last 100)
     */
    @GetMapping
    public List<AuditLog> latest(@RequestParam(defaultValue = "100") int limit) {

        int safeLimit = Math.min(Math.max(limit, 1), 500);

        return repo.findAll(PageRequest.of(
                        0,
                        safeLimit,
                        Sort.by(Sort.Direction.DESC, "ts")
                ))
                .getContent();
    }
}
