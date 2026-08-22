package com.aiguard.ai.gateway.iam.service;

import com.aiguard.ai.gateway.iam.UserRole;
import com.aiguard.ai.gateway.iam.entity.AppUser;
import com.aiguard.ai.gateway.iam.repo.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public List<AppUser> listUsers(String tenantId) {
        return repo.findAll().stream().filter(u -> u.getTenantId().equals(normalizeTenant(tenantId))).toList();
    }

    public AppUser upsertUser(String tenantId, String email, UserRole role, boolean enabled) {
        if (email == null || email.isBlank()) throw new IllegalArgumentException("email required");
        if (role == null) throw new IllegalArgumentException("role required");

        return repo.save(AppUser.builder()
                .id(key(normalizeTenant(tenantId), normalizeEmail(email)))
                .tenantId(normalizeTenant(tenantId))
                .email(normalizeEmail(email))
                .role(role)
                .enabled(enabled)
                .build());
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
