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

        seed("admin@aiguard.com", UserRole.ADMIN);
        seed("intern@aiguard.com", UserRole.INTERN);
        seed("finance@aiguard.com", UserRole.FINANCE);
        seed("engineer@aiguard.com", UserRole.ENGINEER);
    }

    private void seed(String email, UserRole role) {
        repo.findById(email).orElseGet(() -> repo.save(
                AppUser.builder()
                        .email(email)
                        .role(role)
                        .enabled(true)
                        .build()
        ));
    }
}
