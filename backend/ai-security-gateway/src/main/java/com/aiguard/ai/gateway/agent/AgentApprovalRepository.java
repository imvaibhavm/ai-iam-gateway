package com.aiguard.ai.gateway.agent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AgentApprovalRepository extends JpaRepository<AgentApproval,String> {
    List<AgentApproval> findByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, ApprovalStatus status);
    List<AgentApproval> findByRunIdOrderByCreatedAtDesc(String runId);
}
