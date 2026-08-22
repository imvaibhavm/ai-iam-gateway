package com.aiguard.ai.gateway.iam;

import com.aiguard.ai.gateway.iam.entity.AppUser;
import com.aiguard.ai.gateway.iam.service.AppUserService;
import lombok.RequiredArgsConstructor;
import com.aiguard.ai.gateway.identity.IdentityResolver;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final AppUserService userService;
    private final IdentityResolver identityResolver;

    @GetMapping("/me")
    public AppUser me(Authentication authentication) {
        var identity = identityResolver.require(authentication);
        return userService.getOrCreate(identity.tenantId(), identity.email(), identity.role());
    }
}
