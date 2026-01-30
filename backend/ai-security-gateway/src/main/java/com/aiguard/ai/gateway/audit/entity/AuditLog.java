package com.aiguard.ai.gateway.audit.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "audit_log")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private Instant ts;

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
}
