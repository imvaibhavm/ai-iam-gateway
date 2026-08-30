package com.aiguard.ai.gateway.mcp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "mcp_provider_settings")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class McpProviderSetting {
    @Id
    private String id;
    @Column(nullable = false)
    private String tenantId;
    @Column(nullable = false)
    private String providerId;
    @Column(nullable = false)
    private boolean adminEnabled;
}
