package com.aiguard.ai.gateway.admin;

import com.aiguard.ai.gateway.admin.dto.UpsertUserRequest;
import com.aiguard.ai.gateway.iam.UserRole;
import com.aiguard.ai.gateway.iam.entity.AppUser;
import com.aiguard.ai.gateway.iam.service.AppUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AppUserService userService;

    @GetMapping
    public List<AppUser> list() {
        return userService.listUsers();
    }

    @PostMapping
    public AppUser upsert(@RequestBody UpsertUserRequest req) {
        boolean enabled = req.enabled() == null || req.enabled();
        return userService.upsertUser(req.email(), req.role(), enabled);
    }

    @PutMapping("/{email}/role/{role}")
    public AppUser updateRole(@PathVariable String email, @PathVariable String role) {
        return userService.updateRole(email, UserRole.valueOf(role.toUpperCase()));
    }

    @PutMapping("/{email}/enabled/{enabled}")
    public AppUser updateEnabled(@PathVariable String email, @PathVariable boolean enabled) {
        return userService.updateEnabled(email, enabled);
    }
}
