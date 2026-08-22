package com.aiguard.ai.gateway.iam.repo;

import com.aiguard.ai.gateway.iam.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, String> {
    java.util.Optional<AppUser> findByExternalIssuerAndExternalSubject(String issuer, String subject);
    java.util.List<AppUser> findByEmailIgnoreCase(String email);
}
