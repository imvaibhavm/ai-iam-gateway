package com.aiguard.ai.gateway.agent;

import com.aiguard.ai.gateway.iam.UserRole;
import com.aiguard.ai.gateway.identity.IdentityType;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "agent_run")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AgentRun {
    @Id private String id;
    @Column(nullable=false, unique=true) private String requestId;
    @Column(nullable=false) private String tenantId;
    @Column(nullable=false) private String agentId;
    @Column(nullable=false) private String originatingSubject;
    @Column(nullable=false) private String email;
    @Enumerated(EnumType.STRING) private UserRole role;
    @Enumerated(EnumType.STRING) private IdentityType identityType;
    @Column(length=2000) private String delegationChain;
    @Column(length=2000) private String effectiveScopes;
    @Column(length=4000) private String prompt;
    private int stepCount;
    private int maxSteps;
    @Enumerated(EnumType.STRING) private AgentRunStatus status;
    private String provider;
    private String policyResult;
    @Column(length=8000) private String response;
    private String traceId;
    private Instant createdAt;
    private Instant updatedAt;
}
