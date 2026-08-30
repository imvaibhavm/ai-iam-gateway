package com.aiguard.ai.gateway.audit.controller;

import com.aiguard.ai.gateway.audit.entity.AuditLog;
import com.aiguard.ai.gateway.audit.repo.AuditLogRepository;
import com.aiguard.ai.gateway.identity.IdentityResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/audit/metrics")
@RequiredArgsConstructor
public class AuditMetricsController {
    private final AuditLogRepository audits;
    private final IdentityResolver identities;

    @GetMapping
    public AuditMetrics metrics(@RequestParam(defaultValue = "24h") String period, Authentication auth) {
        Period selected = Period.parse(period);
        Instant now = Instant.now();
        List<AuditLog> records = audits.findByTenantIdAndTsAfterOrderByTsAsc(
                identities.require(auth).tenantId(), now.minus(selected.amount(), selected.unit()), PageRequest.of(0, 10_000));
        long allowed = records.stream().filter(AuditLog::isAllowed).count();
        long piiHandled = records.stream().filter(log -> log.getPiiTypes() != null && !log.getPiiTypes().isBlank()).count();
        Map<String, Long> piiByType = new LinkedHashMap<>();
        records.forEach(log -> splitTypes(log.getPiiTypes()).forEach(type -> piiByType.merge(type, 1L, Long::sum)));

        int buckets = selected == Period.DAY ? 24 : selected == Period.WEEK ? 7 : 30;
        ChronoUnit bucketUnit = selected == Period.DAY ? ChronoUnit.HOURS : ChronoUnit.DAYS;
        Instant start = now.minus(buckets, bucketUnit);
        List<DecisionPoint> trend = new ArrayList<>();
        for (int index = 0; index < buckets; index++) {
            Instant from = start.plus(index, bucketUnit);
            Instant to = from.plus(1, bucketUnit);
            long bucketAllowed = records.stream().filter(log -> inBucket(log, from, to) && log.isAllowed()).count();
            long bucketDenied = records.stream().filter(log -> inBucket(log, from, to) && !log.isAllowed()).count();
            trend.add(new DecisionPoint(from.toString(), bucketAllowed, bucketDenied));
        }
        return new AuditMetrics(selected.id(), records.size(), allowed, records.size() - allowed, piiHandled,
                records.stream().filter(AuditLog::isOutputRedacted).count(), piiByType, trend);
    }

    private boolean inBucket(AuditLog log, Instant from, Instant to) {
        return log.getTs() != null && !log.getTs().isBefore(from) && log.getTs().isBefore(to);
    }

    private List<String> splitTypes(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split(",")).map(String::trim).filter(type -> !type.isBlank())
                .map(type -> type.toUpperCase(Locale.ROOT)).distinct().toList();
    }

    public record AuditMetrics(String period, long total, long allowed, long denied, long piiHandled,
                               long outputRedactions, Map<String, Long> piiByType, List<DecisionPoint> trend) { }
    public record DecisionPoint(String bucket, long allowed, long denied) { }

    private enum Period {
        DAY("24h", 24, ChronoUnit.HOURS), WEEK("7d", 7, ChronoUnit.DAYS), MONTH("30d", 30, ChronoUnit.DAYS);
        private final String id; private final long amount; private final ChronoUnit unit;
        Period(String id, long amount, ChronoUnit unit) { this.id = id; this.amount = amount; this.unit = unit; }
        static Period parse(String value) {
            for (Period period : values()) if (period.id.equalsIgnoreCase(value)) return period;
            throw new IllegalArgumentException("period must be one of 24h, 7d or 30d");
        }
        String id() { return id; } long amount() { return amount; } ChronoUnit unit() { return unit; }
    }
}
