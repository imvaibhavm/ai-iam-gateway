package com.aiguard.ai.gateway.chat.service;

import com.aiguard.ai.gateway.audit.entity.AuditLog;
import com.aiguard.ai.gateway.audit.service.AuditService;
import com.aiguard.ai.gateway.guard.intent.*;
import com.aiguard.ai.gateway.guard.output.OutputInspector;
import com.aiguard.ai.gateway.guard.pii.*;
import com.aiguard.ai.gateway.guard.policy.PolicyDecision;
import com.aiguard.ai.gateway.guard.policy.DataClassification;
import com.aiguard.ai.gateway.guard.policy.PolicyContext;
import com.aiguard.ai.gateway.guard.policy.PolicyObligation;
import com.aiguard.ai.gateway.iam.PolicyEngine;
import com.aiguard.ai.gateway.iam.entity.AppUser;
import com.aiguard.ai.gateway.iam.service.AppUserService;
import com.aiguard.ai.gateway.identity.IdentityContext;
import com.aiguard.ai.gateway.provider.*;
import com.aiguard.ai.gateway.routing.*;
import com.aiguard.ai.gateway.usage.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import com.aiguard.ai.gateway.observability.AiTelemetry;

@Service
public class ChatService {
    private final PolicyEngine policyEngine; private final AppUserService users;
    private final IntentClassifier classifier; private final PiiDetector piiDetector;
    private final PolicyAwareModelRouter router; private final ProviderExecutor executor;
    private final OutputInspector outputInspector; private final AuditService audits;
    private final RateLimitService rateLimits; private final UsageBudgetService budgets;
    private final String policyVersion;
    private final AiTelemetry telemetry;

    public ChatService(PolicyEngine policyEngine, AppUserService users, IntentClassifier classifier,
            PiiDetector piiDetector, PolicyAwareModelRouter router, ProviderExecutor executor,
            OutputInspector outputInspector, AuditService audits, RateLimitService rateLimits,
            UsageBudgetService budgets, @Value("${gateway.policy-version}") String policyVersion,
            AiTelemetry telemetry) {
        this.policyEngine = policyEngine; this.users = users; this.classifier = classifier;
        this.piiDetector = piiDetector; this.router = router; this.executor = executor;
        this.outputInspector = outputInspector; this.audits = audits; this.rateLimits = rateLimits;
        this.budgets = budgets; this.policyVersion = policyVersion; this.telemetry = telemetry;
    }

    public String reply(IdentityContext identity, String message) { return process(identity, message, null, UUID.randomUUID().toString()); }
    public String reply(IdentityContext identity, String message, String requestId) { return process(identity, message, null, requestId); }
    public void streamReply(IdentityContext identity, String message, Consumer<String> sink) { process(identity, message, sink, UUID.randomUUID().toString()); }

    private String process(IdentityContext identity, String message, Consumer<String> streamSink, String requestId) {
        return telemetry.observe("ai.request", Map.of("ai.request.id", requestId,
                "ai.tenant.id", identity.tenantId(), "ai.identity.type", identity.type(),
                "ai.identity.subject", identity.subject()), () -> processObserved(identity, message, streamSink, requestId));
    }

    private String processObserved(IdentityContext identity, String message, Consumer<String> streamSink, String requestId) {
        AppUser user = users.requireEnabled(identity.tenantId(), identity.email());
        if (!user.isEnabled()) { audits.save(baseAudit(requestId, identity, user).allowed(false).decisionReason("user_disabled").build()); return emit("⛔ User is disabled.", streamSink); }

        rateLimits.check(identity.tenantId(), identity.subject());
        PiiResult pii = telemetry.observe("ai.security.input_scan", Map.of("ai.request.id", requestId),
                () -> piiDetector.detectAndMask(message));
        String safePrompt = pii.maskedText();
        IntentClassification classification = pii.hasPii()
                ? new IntentClassification(IntentType.PII, 1.0, "pii_detector_override", "N/A")
                : telemetry.observe("ai.intent.classify", Map.of("ai.request.id", requestId), () -> classifier.classify(safePrompt));
        IdentityContext effectiveIdentity = identity.withRole(user.getRole());
        DataClassification dataClassification = switch (classification.intent()) {
            case PII, SECRETS -> DataClassification.RESTRICTED;
            case HR, FINANCE -> DataClassification.CONFIDENTIAL;
            default -> DataClassification.INTERNAL;
        };
        PolicyDecision policy = telemetry.observe("ai.policy.evaluate", Map.of("ai.request.id", requestId,
                "ai.intent", classification.intent(), "ai.data.classification", dataClassification),
                () -> policyEngine.evaluate(PolicyContext.llm(effectiveIdentity, classification, dataClassification)));
        AuditLog.AuditLogBuilder audit = baseAudit(requestId, identity, user)
                .intent(classification.intent().name()).confidence(classification.confidence())
                .allowed(policy.allowed()).decisionReason(policy.reason())
                .policyVersion(policy.policyVersion())
                .piiTypes(pii.entities().stream().map(e -> e.type().name()).distinct().collect(Collectors.joining(",")));
        if (!policy.allowed()) { audits.save(audit.build()); return emit(deny(user, classification, policy), streamSink); }

        var reservation = budgets.reserve(identity.tenantId(), safePrompt);
        RoutingDecision routing = telemetry.observe("ai.model.route", Map.of("ai.request.id", requestId,
                "ai.policy.decision", policy.allowed(), "ai.policy.version", policyVersion),
                () -> router.select(classification.intent(), policy));
        audit.provider(routing.selected().providerId()).model(routing.selected().modelId()).routingReason(routing.reason());
        StringBuilder providerOutput = new StringBuilder();
        try {
            ModelRequest request = new ModelRequest(requestId, safePrompt,
                    outputTokenLimit(policy, reservation.maxOutputTokens()),
                    Map.of("tenantId", identity.tenantId(), "intent", classification.intent().name()));
            var execution = telemetry.observe("ai.model.inference", Map.of("ai.request.id", requestId,
                    "ai.model.provider", routing.selected().providerId(), "ai.model.name", routing.selected().modelId(),
                    "ai.model.route_reason", routing.reason()),
                    () -> executor.execute(routing, request, streamSink != null, providerOutput::append));
            ModelResponse response = execution.response();
            String raw = streamSink == null ? response.content() : providerOutput.toString();
            var inspected = outputInspector.inspect(raw);
            audit.provider(response.provider()).model(response.model())
                    .routingReason(execution.fallbackUsed() ? routing.reason() + ":fallback" : routing.reason())
                    .latencyMs(response.latencyMs())
                    .inputTokens(response.inputTokens() > 0 ? response.inputTokens() : reservation.estimatedInputTokens())
                    .outputTokens(response.outputTokens() > 0 ? response.outputTokens() : Math.max(1, raw.length() / 4))
                    .estimatedCostUsd(response.estimatedCostUsd()).outputRedacted(inspected.redacted()).providerSucceeded(true);
            audits.save(audit.build());
            return emit(inspected.safeOutput(), streamSink);
        } catch (RuntimeException ex) {
            audits.save(audit.providerSucceeded(false).build());
            return emit("❌ No policy-eligible model provider is currently available.", streamSink);
        }
    }

    private AuditLog.AuditLogBuilder baseAudit(String requestId, IdentityContext identity, AppUser user) {
        return AuditLog.builder().requestId(requestId).tenantId(identity.tenantId()).userEmail(identity.email())
                .role(user.getRole()).policyVersion(policyVersion);
    }
    private String emit(String text, Consumer<String> sink) { if (sink != null) sink.accept(text); return text; }
    private int outputTokenLimit(PolicyDecision policy, int reserved) {
        return policy.obligations().stream()
                .filter(value -> value.type() == PolicyObligation.Type.LIMIT_OUTPUT_TOKENS)
                .map(value -> value.parameters().get("tokens"))
                .filter(Objects::nonNull)
                .mapToInt(value -> { try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return reserved; } })
                .findFirst().stream().map(value -> Math.min(value, reserved)).findFirst().orElse(reserved);
    }
    private String deny(AppUser user, IntentClassification ic, PolicyDecision decision) {
        return "⛔ Access denied by policy.\n\n• Role: " + user.getRole() + "\n• Intent: " + ic.intent()
                + "\n• Confidence: " + String.format("%.2f", ic.confidence()) + "\n• Reason: " + decision.reason();
    }
}
