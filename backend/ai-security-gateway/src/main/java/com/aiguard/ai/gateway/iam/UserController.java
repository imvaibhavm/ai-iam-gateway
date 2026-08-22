package com.aiguard.ai.gateway.iam;

import lombok.RequiredArgsConstructor;
import com.aiguard.ai.gateway.identity.IdentityResolver;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final IdentityResolver identityResolver;

    @GetMapping("/me")
    public CurrentIdentityResponse me(Authentication authentication) {
        var identity = identityResolver.require(authentication);
        return new CurrentIdentityResponse(identity.subject(), identity.email(), identity.tenantId(),
                identity.role(), identity.type(), identity.groups(), identity.attributes());
    }

    public record CurrentIdentityResponse(String subject, String email, String tenantId, UserRole role,
                                          com.aiguard.ai.gateway.identity.IdentityType type,
                                          java.util.Set<String> groups,
                                          java.util.Map<String, String> attributes) { }
}
