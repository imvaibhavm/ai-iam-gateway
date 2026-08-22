package com.aiguard.ai.gateway.usage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimitService {
    private final int limit;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    public RateLimitService(@Value("${gateway.rate-limit-per-minute:30}") int limit) { this.limit = limit; }
    public void check(String tenantId, String subject) {
        long minute = Instant.now().getEpochSecond() / 60;
        Window window = windows.compute(tenantId + "|" + subject,
                (key, old) -> old == null || old.minute != minute ? new Window(minute) : old);
        if (window.count.incrementAndGet() > limit) throw new IllegalStateException("Rate limit exceeded");
    }
    private static class Window { final long minute; final AtomicInteger count = new AtomicInteger(); Window(long minute) { this.minute = minute; } }
}
