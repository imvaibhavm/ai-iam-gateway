package com.aiguard.ai.gateway.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Emits one JSON object per line for collection by an OTEL/log forwarder. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "gateway.observability.security-event-log-enabled", havingValue = "true")
public class LoggingSecurityEventExporter implements SecurityEventExporter {
    private static final Logger SECURITY_LOG = LoggerFactory.getLogger("SECURITY_EVENT");
    private final ObjectMapper objectMapper;

    @Override public String exporterId() { return "structured-log"; }

    @Override
    public void export(SecurityEvent event) {
        try {
            SECURITY_LOG.info(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize security event", exception);
        }
    }
}
