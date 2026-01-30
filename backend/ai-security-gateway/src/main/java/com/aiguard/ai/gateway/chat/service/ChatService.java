package com.aiguard.ai.gateway.chat.service;

import com.aiguard.ai.gateway.guard.intent.IntentClassification;
import com.aiguard.ai.gateway.guard.intent.IntentClassifier;
import com.aiguard.ai.gateway.guard.policy.PolicyDecision;
import com.aiguard.ai.gateway.iam.PolicyEngine;
import com.aiguard.ai.gateway.iam.UserRole;
import com.aiguard.ai.gateway.iam.entity.AppUser;
import com.aiguard.ai.gateway.iam.service.AppUserService;
import com.aiguard.ai.gateway.llm.LlmRouter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final PolicyEngine policyEngine;          // ✅ IAM policy engine
    private final AppUserService userService;
    private final LlmRouter llm;
    private final IntentClassifier intentClassifier;  // ✅ LLM-based intent classifier

    /**
     * Non-streaming chat reply
     */
    public String reply(String userEmail, String userMessage) {

        AppUser user = userService.getOrCreateDefault(userEmail);

        if (!user.isEnabled()) {
            return "⛔ User is disabled.";
        }

        UserRole role = user.getRole();

        // ✅ semantic intent classification (LLM based)
        IntentClassification ic = intentClassifier.classify(userMessage);

        // ✅ role + intent policy evaluation
        PolicyDecision decision = policyEngine.evaluate(role, ic);

        if (!decision.allowed()) {
            return denyMessage(role, ic, decision);
        }
        log.info("Intent classification: user={} role={} intent={} conf={} reason={}",
        userEmail, role, ic.intent(), ic.confidence(), ic.reason());



        return llm.generate(userMessage);
    }

    /**
     * Streaming chat reply (token by token)
     */
    public void streamReply(String userEmail, String userMessage, Consumer<String> onToken) {

        AppUser user = userService.getOrCreateDefault(userEmail);

        if (!user.isEnabled()) {
            onToken.accept("⛔ User is disabled.");
            return;
        }

        UserRole role = user.getRole();

        // ✅ semantic intent classification (LLM based)
        IntentClassification ic = intentClassifier.classify(userMessage);

        // ✅ role + intent policy evaluation
        PolicyDecision decision = policyEngine.evaluate(role, ic);

        if (!decision.allowed()) {
            onToken.accept(denyMessage(role, ic, decision));
            return;
        }

        log.info("Policy decision: user={} role={} allowed={} reason={}",
        userEmail, role, decision.allowed(), decision.reason());

        llm.stream(userMessage, onToken);
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
