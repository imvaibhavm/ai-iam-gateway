package com.aiguard.ai.gateway.iam;

import com.aiguard.ai.gateway.iam.entity.AppUser;
import com.aiguard.ai.gateway.iam.service.AppUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final AppUserService userService;

    @GetMapping("/me")
    public AppUser me(@RequestHeader("X-User-Email") String email) {
        return userService.getOrCreateDefault(email);
    }
}
