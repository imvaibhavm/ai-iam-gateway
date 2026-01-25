package com.aiguard.ai.gateway.iam;

import org.springframework.stereotype.Component;

@Component
public class PolicyEngine {

    public boolean isAllowed(UserRole role, String prompt) {
        if (prompt == null) return true;

        String p = prompt.toLowerCase();

        // Demo restricted topics
        boolean asksRevenue = p.contains("revenue") || p.contains("forecast") || p.contains("q4");
        boolean asksSalary = p.contains("salary") || p.contains("compensation") || p.contains("ctc");

        // Salary info only ADMIN in POC
        if (asksSalary) {
            return role == UserRole.ADMIN;
        }

        // Revenue/forecast only FINANCE and ADMIN in POC
        if (asksRevenue) {
            return role == UserRole.FINANCE || role == UserRole.ADMIN;
        }

        // Default allow
        return true;
    }

    public String denyMessage(UserRole role) {
        return "⛔ Access denied: Your role (" + role + ") is not allowed to access this information.";
    }
}
