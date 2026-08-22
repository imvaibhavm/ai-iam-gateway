package com.aiguard.ai.gateway.identity;

import org.springframework.security.access.AccessDeniedException;

public class IdentityResolutionException extends AccessDeniedException {
    public IdentityResolutionException(String message) { super(message); }
}
