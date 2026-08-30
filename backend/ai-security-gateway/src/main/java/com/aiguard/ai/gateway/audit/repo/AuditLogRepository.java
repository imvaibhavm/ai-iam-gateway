package com.aiguard.ai.gateway.audit.repo;

import com.aiguard.ai.gateway.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface AuditLogRepository extends JpaRepository<AuditLog, String> {
    List<AuditLog> findByTenantId(String tenantId, Pageable pageable);
    Optional<AuditLog> findTopByRequestIdOrderByTsDesc(String requestId);
    List<AuditLog> findByTenantIdAndTsAfterOrderByTsAsc(String tenantId, Instant after, Pageable pageable);
}
