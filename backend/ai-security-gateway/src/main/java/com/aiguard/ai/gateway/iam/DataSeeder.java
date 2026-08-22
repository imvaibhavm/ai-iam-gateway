package com.aiguard.ai.gateway.iam;

import com.aiguard.ai.gateway.iam.entity.AppUser;
import com.aiguard.ai.gateway.iam.repo.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final AppUserRepository repo;

    @Override
    public void run(String... args) {

        seed("default", "admin@aiguard.com", UserRole.ADMIN);
        seed("default", "intern@aiguard.com", UserRole.INTERN);
        seed("default", "finance@aiguard.com", UserRole.FINANCE);
        seed("default", "engineer@aiguard.com", UserRole.ENGINEER);
    }

    private void seed(String tenant, String email, UserRole role) {
        String id = tenant + "|" + email;
        repo.findById(id).orElseGet(() -> repo.save(
                AppUser.builder()
                        .id(id)
                        .tenantId(tenant)
                        .email(email)
                        .role(role)
                        .enabled(true)
                        .build()
        ));
    }
}
