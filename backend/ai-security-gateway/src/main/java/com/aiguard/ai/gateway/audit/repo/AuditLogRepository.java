package com.aiguard.ai.gateway.audit.repo;

import com.aiguard.ai.gateway.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, String> {
}
