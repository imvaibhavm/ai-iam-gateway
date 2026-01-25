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
    public AppUser getOrCreateDefault(String email) {
        if (email == null || email.isBlank()) {
            email = "anonymous@local";
        }

        final String finalEmail = email.trim().toLowerCase();

        return repo.findById(finalEmail)
                .orElseGet(() -> repo.save(
                        AppUser.builder()
                                .email(finalEmail)
                                .role(UserRole.INTERN)
                                .enabled(true)
                                .build()
                ));
    }

    public List<AppUser> listUsers() {
        return repo.findAll();
    }

    public AppUser upsertUser(String email, UserRole role, boolean enabled) {
        if (email == null || email.isBlank()) throw new IllegalArgumentException("email required");
        if (role == null) throw new IllegalArgumentException("role required");

        return repo.save(AppUser.builder()
                .email(email.trim().toLowerCase())
                .role(role)
                .enabled(enabled)
                .build());
    }

    public AppUser updateRole(String email, UserRole role) {
        AppUser user = repo.findById(email.trim().toLowerCase()).orElseThrow();
        user.setRole(role);
        return repo.save(user);
    }

    public AppUser updateEnabled(String email, boolean enabled) {
        AppUser user = repo.findById(email.trim().toLowerCase()).orElseThrow();
        user.setEnabled(enabled);
        return repo.save(user);
    }
}
