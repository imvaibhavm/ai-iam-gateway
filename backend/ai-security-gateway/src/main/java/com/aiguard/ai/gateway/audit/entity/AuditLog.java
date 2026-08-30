package com.aiguard.ai.gateway.audit.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "audit_log", indexes = @Index(name = "ix_audit_tenant_ts", columnList = "tenant_id,ts"))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private Instant ts;

    private String requestId;
    private String tenantId;

    private String userEmail;

    @Enumerated(EnumType.STRING)
    private com.aiguard.ai.gateway.iam.UserRole role;

    private String intent;
    private double confidence;

    private boolean allowed;
    private String decisionReason;

    @Column(length = 1000)
    private String piiTypes; // "EMAIL,PHONE,UUID"

    private String provider; // huggingface/ollama
    private String model;
    private String routingReason;
    private long latencyMs;
    private long inputTokens;
    private long outputTokens;
    private double estimatedCostUsd;
    private String policyVersion;
    private boolean outputRedacted;
    private boolean providerSucceeded;
}
