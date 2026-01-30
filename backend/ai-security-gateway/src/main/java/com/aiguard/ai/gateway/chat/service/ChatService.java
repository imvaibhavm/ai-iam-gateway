package com.aiguard.ai.gateway.chat.service;

import com.aiguard.ai.gateway.audit.entity.AuditLog;
import com.aiguard.ai.gateway.audit.service.AuditService;
import com.aiguard.ai.gateway.guard.intent.IntentClassification;
import com.aiguard.ai.gateway.guard.intent.IntentClassifier;
import com.aiguard.ai.gateway.guard.pii.PiiDetector;
import com.aiguard.ai.gateway.guard.pii.PiiResult;
import com.aiguard.ai.gateway.guard.policy.PolicyDecision;
import com.aiguard.ai.gateway.iam.PolicyEngine;
import com.aiguard.ai.gateway.iam.UserRole;
import com.aiguard.ai.gateway.iam.entity.AppUser;
import com.aiguard.ai.gateway.iam.service.AppUserService;
import com.aiguard.ai.gateway.llm.LlmRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final PolicyEngine policyEngine;
    private final AppUserService userService;
    private final LlmRouter llm;
    private final IntentClassifier intentClassifier;
    private final PiiDetector piiDetector;
    private final AuditService auditService;

    /**
     * Non-streaming chat reply
     */
    public String reply(String userEmail, String userMessage) {

        AppUser user = userService.getOrCreateDefault(userEmail);

        if (!user.isEnabled()) {
            auditService.save(AuditLog.builder()
                    .userEmail(userEmail)
                    .role(user.getRole())
                    .allowed(false)
                    .decisionReason("user_disabled")
                    .provider(llm.providerName())
                    .build());
            return "⛔ User is disabled.";
        }

        UserRole role = user.getRole();

        // ✅ Mask PII before ANY LLM interaction
        PiiResult pii = piiDetector.detectAndMask(userMessage);
        String maskedMsg = pii.maskedText();
        IntentClassification ic;

if (pii.hasPii()) {
    ic = new IntentClassification(
            com.aiguard.ai.gateway.guard.intent.IntentType.PII,
            1.0,
            "pii_detector_override",
            "N/A"
    );
} else {
    ic = intentClassifier.classify(maskedMsg);
}


        

        // ✅ policy decision
        PolicyDecision decision = policyEngine.evaluate(role, ic);

        log.info("Intent classification: user={} role={} intent={} conf={} reason={}",
                userEmail, role, ic.intent(), ic.confidence(), ic.reason());

        log.info("Policy decision: user={} role={} allowed={} reason={}",
                userEmail, role, decision.allowed(), decision.reason());

        // ✅ audit log (always)
        auditService.save(AuditLog.builder()
                .userEmail(userEmail)
                .role(role)
                .intent(ic.intent().name())
                .confidence(ic.confidence())
                .allowed(decision.allowed())
                .decisionReason(decision.reason())
                .piiTypes(pii.entities().stream()
                        .map(e -> e.type().name())
                        .distinct()
                        .collect(Collectors.joining(",")))
                .provider(llm.providerName())
                .build());

        if (!decision.allowed()) {
            return denyMessage(role, ic, decision);
        }

        return llm.generate(maskedMsg);
    }

    /**
     * Streaming chat reply (token by token)
     */
    public void streamReply(String userEmail, String userMessage, Consumer<String> onToken) {

        AppUser user = userService.getOrCreateDefault(userEmail);

        if (!user.isEnabled()) {
            auditService.save(AuditLog.builder()
                    .userEmail(userEmail)
                    .role(user.getRole())
                    .allowed(false)
                    .decisionReason("user_disabled")
                    .provider(llm.providerName())
                    .build());
            onToken.accept("⛔ User is disabled.");
            return;
        }

        UserRole role = user.getRole();

        // ✅ Mask PII before ANY LLM interaction
        // ✅ Mask PII before ANY LLM interaction
PiiResult pii = piiDetector.detectAndMask(userMessage);
String maskedMsg = pii.maskedText();

// ✅ Intent classification (PII override > LLM classifier)
IntentClassification ic;
if (pii.hasPii()) {
    ic = new IntentClassification(
            com.aiguard.ai.gateway.guard.intent.IntentType.PII,
            1.0,
            "pii_detector_override",
            "N/A"
    );
} else {
    ic = intentClassifier.classify(maskedMsg);
}


        // ✅ policy decision
        PolicyDecision decision = policyEngine.evaluate(role, ic);

        log.info("Intent classification: user={} role={} intent={} conf={} reason={}",
                userEmail, role, ic.intent(), ic.confidence(), ic.reason());

        log.info("Policy decision: user={} role={} allowed={} reason={}",
                userEmail, role, decision.allowed(), decision.reason());

        // ✅ audit log (always)
        auditService.save(AuditLog.builder()
                .userEmail(userEmail)
                .role(role)
                .intent(ic.intent().name())
                .confidence(ic.confidence())
                .allowed(decision.allowed())
                .decisionReason(decision.reason())
                .piiTypes(pii.entities().stream()
                        .map(e -> e.type().name())
                        .distinct()
                        .collect(Collectors.joining(",")))
                .provider(llm.providerName())
                .build());

        if (!decision.allowed()) {
            onToken.accept(denyMessage(role, ic, decision));
            return;
        }

        // Optional: tell user masking happened (nice for demo)
        if (pii.hasPii()) {
            onToken.accept("🔒 Masked sensitive data: " +
                    pii.entities().stream().map(e -> e.type().name()).distinct().toList() +
                    "\n\n");
        }

        llm.stream(maskedMsg, onToken);
    }

    private String denyMessage(UserRole role, IntentClassification ic, PolicyDecision decision) {
        return """
                ⛔ Access denied by policy.

                • Role: %s
                • Intent: %s
                • Confidence: %.2f
                • Reason: %s
                """.formatted(role, ic.intent(), ic.confidence(), decision.reason());
    }
}
