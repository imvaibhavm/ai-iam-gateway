package com.aiguard.ai.gateway.observability;

import com.aiguard.ai.gateway.identity.IdentityResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/security-events")
@RequiredArgsConstructor
public class SecurityAnalyticsController {
    private final InMemorySecurityEventExporter recentEvents;
    private final IdentityResolver identities;

    @GetMapping
    public List<SecurityEvent> recent(@RequestParam(defaultValue = "100") int limit, Authentication auth) {
        return recentEvents.recentForTenant(identities.require(auth).tenantId(), limit);
    }

    @GetMapping("/summary")
    public SecurityEventSummary summary(@RequestParam(defaultValue = "500") int window, Authentication auth) {
        List<SecurityEvent> events = recentEvents.recentForTenant(identities.require(auth).tenantId(), window);
        long denied = events.stream().filter(event -> !event.allowed()).count();
        long failures = events.stream().filter(event -> event.provider() != null && !event.providerSucceeded()).count();
        long redacted = events.stream().filter(SecurityEvent::outputRedacted).count();
        Map<String, Long> byIntent = events.stream().collect(Collectors.groupingBy(
                event -> event.intent() == null ? "unknown" : event.intent(), Collectors.counting()));
        return new SecurityEventSummary(events.size(), denied, failures, redacted, byIntent);
    }

    public record SecurityEventSummary(long total, long denied, long providerFailures,
                                       long outputRedactions, Map<String, Long> byIntent) {}
}
