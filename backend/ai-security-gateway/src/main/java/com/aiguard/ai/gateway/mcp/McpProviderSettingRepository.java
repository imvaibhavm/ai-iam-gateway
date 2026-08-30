package com.aiguard.ai.gateway.mcp;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface McpProviderSettingRepository extends JpaRepository<McpProviderSetting, String> {
    List<McpProviderSetting> findByTenantId(String tenantId);
}
