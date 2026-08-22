package com.aiguard.ai.gateway.audit.service;

import com.aiguard.ai.gateway.audit.entity.AuditLog;
import com.aiguard.ai.gateway.audit.repo.AuditLogRepository;
import com.aiguard.ai.gateway.observability.SecurityEvent;
import com.aiguard.ai.gateway.observability.SecurityEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repo;
    private final SecurityEventPublisher securityEvents;

    public AuditLog save(AuditLog log) {
        log.setTs(Instant.now());
        AuditLog saved = repo.save(log);
        securityEvents.publish(SecurityEvent.from(saved));
        return saved;
    }
}
