package com.aiguard.ai.gateway.observability;

/** Extension point for OTEL collectors, syslog, Kafka, or vendor SIEM adapters. */
public interface SecurityEventExporter {
    String exporterId();
    void export(SecurityEvent event);
}
