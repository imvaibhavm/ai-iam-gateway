package com.aiguard.ai.gateway.agent;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "agent_approval")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AgentApproval {
    @Id private String id;
    @Column(nullable=false) private String runId;
    @Column(nullable=false) private String requestId;
    @Column(nullable=false) private String tenantId;
    @Column(nullable=false) private String toolName;
    @Column(length=4000) private String sanitizedArguments;
    @Column(length=1000) private String reason;
    @Enumerated(EnumType.STRING) private ToolRiskSnapshot risk;
    @Enumerated(EnumType.STRING) private ApprovalStatus status;
    private String decidedBy;
    private Instant createdAt;
    private Instant decidedAt;

    public enum ToolRiskSnapshot { LOW, MEDIUM, HIGH, CRITICAL }
}
