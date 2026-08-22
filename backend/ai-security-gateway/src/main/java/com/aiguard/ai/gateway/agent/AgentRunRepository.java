package com.aiguard.ai.gateway.agent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AgentRunRepository extends JpaRepository<AgentRun,String> {
    List<AgentRun> findTop100ByTenantIdOrderByCreatedAtDesc(String tenantId);
}
