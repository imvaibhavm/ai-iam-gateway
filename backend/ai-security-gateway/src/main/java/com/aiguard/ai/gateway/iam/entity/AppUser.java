package com.aiguard.ai.gateway.iam.entity;

import com.aiguard.ai.gateway.iam.UserRole;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "app_users", uniqueConstraints = @UniqueConstraint(
        name = "ux_app_users_external_identity", columnNames = {"external_issuer", "external_subject"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

    @Id
    @Column(nullable = false, unique = true)
    private String id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String email;

    // Nullable for pre-OIDC and development users. Bound only after a validated OIDC login.
    @Column(name = "external_issuer")
    private String externalIssuer;

    @Column(name = "external_subject")
    private String externalSubject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(nullable = false)
    private boolean enabled;

    // Server-authoritative ABAC attributes. External claims may supply identity input,
    // but locally administered values take precedence when present.
    private String department;
    private String clearance;
    private String region;

    @Column(name = "policy_assignments", length = 2000)
    private String policyAssignments;
}
