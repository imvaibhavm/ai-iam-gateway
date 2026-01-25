package com.aiguard.ai.gateway.admin.dto;

import com.aiguard.ai.gateway.iam.UserRole;

public record UpsertUserRequest(
        String email,
        UserRole role,
        Boolean enabled
) {}
