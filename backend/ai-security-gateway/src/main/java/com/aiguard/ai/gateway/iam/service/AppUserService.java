package com.aiguard.ai.gateway.iam.service;

import com.aiguard.ai.gateway.iam.UserRole;
import com.aiguard.ai.gateway.iam.entity.AppUser;
import com.aiguard.ai.gateway.iam.repo.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import com.aiguard.ai.gateway.identity.ExternalIdentityClaims;
import com.aiguard.ai.gateway.identity.IdentityResolutionException;

@Service
@RequiredArgsConstructor
public class AppUserService {

    private final AppUserRepository repo;

    /**
     * Returns existing user if present.
     * If not present, creates user with default INTERN role.
     */
    public AppUser getOrCreate(String tenantId, String email, UserRole assertedRole) {
        final String tenant = normalizeTenant(tenantId);
        final String finalEmail = normalizeEmail(email);
        final String id = key(tenant, finalEmail);
        return repo.findById(id)
                .orElseGet(() -> repo.save(
                        AppUser.builder()
                                .id(id)
                                .tenantId(tenant)
                                .email(finalEmail)
                                .role(assertedRole == null ? UserRole.INTERN : assertedRole)
                                .enabled(true)
                                .build()
                ));
    }

    /** Resolve an externally authenticated principal to authoritative local authorization state. */
    public AppUser resolveExternal(ExternalIdentityClaims claims) {
        if (claims.issuer() == null || claims.issuer().isBlank()) {
            throw new IdentityResolutionException("Validated OIDC issuer is required");
        }
        AppUser user = repo.findByExternalIssuerAndExternalSubject(claims.issuer(), claims.subject())
                .orElseGet(() -> bindExistingUser(claims));
        if (!user.isEnabled()) throw new IdentityResolutionException("Local user is disabled");

        String assertedTenant = claims.attributes().get("assertedTenant");
        if (assertedTenant != null && !normalizeTenant(assertedTenant).equals(normalizeTenant(user.getTenantId()))) {
            throw new IdentityResolutionException("OIDC tenant claim does not match authoritative local tenant");
        }
        return user;
    }

    public Optional<AppUser> findBoundExternal(String issuer, String subject) {
        if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) return Optional.empty();
        return repo.findByExternalIssuerAndExternalSubject(issuer, subject);
    }

    public AppUser requireEnabled(String tenantId, String email) {
        AppUser user = repo.findById(key(normalizeTenant(tenantId), normalizeEmail(email)))
                .orElseThrow(() -> new IdentityResolutionException("Local user mapping is required"));
        if (!user.isEnabled()) throw new IdentityResolutionException("Local user is disabled");
        return user;
    }

    private AppUser bindExistingUser(ExternalIdentityClaims claims) {
        List<AppUser> matches = repo.findByEmailIgnoreCase(normalizeEmail(claims.email()));
        if (matches.size() != 1) {
            throw new IdentityResolutionException(matches.isEmpty()
                    ? "No local user mapping exists" : "Email maps to multiple local tenants");
        }
        AppUser user = matches.getFirst();
        if (user.getExternalSubject() != null || user.getExternalIssuer() != null) {
            throw new IdentityResolutionException("Local user is already bound to another external identity");
        }
        user.setExternalIssuer(claims.issuer());
        user.setExternalSubject(claims.subject());
        return repo.save(user);
    }

    public List<AppUser> listUsers(String tenantId) {
        return repo.findAll().stream().filter(u -> u.getTenantId().equals(normalizeTenant(tenantId))).toList();
    }

    public AppUser upsertUser(String tenantId, String email, UserRole role, boolean enabled) {
        if (email == null || email.isBlank()) throw new IllegalArgumentException("email required");
        if (role == null) throw new IllegalArgumentException("role required");
        String tenant = normalizeTenant(tenantId);
        String normalizedEmail = normalizeEmail(email);
        AppUser user = repo.findById(key(tenant, normalizedEmail)).orElseGet(() -> AppUser.builder()
                .id(key(tenant, normalizedEmail)).tenantId(tenant).email(normalizedEmail).build());
        // Preserve any durable (issuer, subject) binding during authorization administration.
        user.setRole(role);
        user.setEnabled(enabled);
        return repo.save(user);
    }

    public AppUser updateRole(String tenantId, String email, UserRole role) {
        AppUser user = repo.findById(key(normalizeTenant(tenantId), normalizeEmail(email))).orElseThrow();
        user.setRole(role);
        return repo.save(user);
    }

    public AppUser updateEnabled(String tenantId, String email, boolean enabled) {
        AppUser user = repo.findById(key(normalizeTenant(tenantId), normalizeEmail(email))).orElseThrow();
        user.setEnabled(enabled);
        return repo.save(user);
    }

    private String normalizeTenant(String tenantId) { return tenantId == null || tenantId.isBlank() ? "default" : tenantId.trim().toLowerCase(); }
    private String normalizeEmail(String email) { if (email == null || email.isBlank()) throw new IllegalArgumentException("email required"); return email.trim().toLowerCase(); }
    private String key(String tenantId, String email) { return tenantId + "|" + email; }
}
