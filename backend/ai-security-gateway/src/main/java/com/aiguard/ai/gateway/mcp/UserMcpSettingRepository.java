package com.aiguard.ai.gateway.mcp;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserMcpSettingRepository extends JpaRepository<UserMcpSetting, String> {
    List<UserMcpSetting> findByTenantIdAndUserEmail(String tenantId, String userEmail);
}
