package com.aiguard.ai.gateway.admin;

import com.aiguard.ai.gateway.admin.dto.UpsertUserRequest;
import com.aiguard.ai.gateway.iam.UserRole;
import com.aiguard.ai.gateway.iam.entity.AppUser;
import com.aiguard.ai.gateway.iam.service.AppUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.aiguard.ai.gateway.identity.IdentityResolver;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AppUserService userService;
    private final IdentityResolver identityResolver;

    @GetMapping
    public List<AppUser> list(Authentication auth) {
        return userService.listUsers(identityResolver.require(auth).tenantId());
    }

    @PostMapping
    public AppUser upsert(@RequestBody UpsertUserRequest req, Authentication auth) {
        boolean enabled = req.enabled() == null || req.enabled();
        return userService.upsertUser(identityResolver.require(auth).tenantId(), req.email(), req.role(), enabled);
    }

    @PostMapping("/import")
    public BulkImportResponse importUsers(@RequestBody BulkImportRequest request, Authentication auth) {
        List<AppUser> users = userService.importUsers(identityResolver.require(auth).tenantId(),
                request == null ? null : request.users());
        return new BulkImportResponse(users.size(), users);
    }

    @PutMapping("/{email}/role/{role}")
    public AppUser updateRole(@PathVariable String email, @PathVariable String role, Authentication auth) {
        return userService.updateRole(identityResolver.require(auth).tenantId(), email, UserRole.valueOf(role.toUpperCase()));
    }

    @PutMapping("/{email}/enabled/{enabled}")
    public AppUser updateEnabled(@PathVariable String email, @PathVariable boolean enabled, Authentication auth) {
        return userService.updateEnabled(identityResolver.require(auth).tenantId(), email, enabled);
    }


    public record BulkImportRequest(List<AppUserService.BulkUserDefinition> users) { }
    public record BulkImportResponse(int imported, List<AppUser> users) { }
}
