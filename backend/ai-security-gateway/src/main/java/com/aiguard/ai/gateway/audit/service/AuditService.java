package com.aiguard.ai.gateway.audit.service;

import com.aiguard.ai.gateway.audit.entity.AuditLog;
import com.aiguard.ai.gateway.audit.repo.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repo;

    public AuditLog save(AuditLog log) {
        log.setTs(Instant.now());
        return repo.save(log);
    }
}
