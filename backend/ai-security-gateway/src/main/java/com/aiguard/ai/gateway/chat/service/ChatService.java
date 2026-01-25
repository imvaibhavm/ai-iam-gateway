package com.aiguard.ai.gateway.chat.service;

import com.aiguard.ai.gateway.iam.PolicyEngine;
import com.aiguard.ai.gateway.iam.UserRole;
import com.aiguard.ai.gateway.iam.entity.AppUser;
import com.aiguard.ai.gateway.iam.service.AppUserService;
import com.aiguard.ai.gateway.ollama.OllamaClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.aiguard.ai.gateway.llm.LlmRouter;

import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final OllamaClient ollamaClient;
    private final PolicyEngine policyEngine;
    private final AppUserService userService;
    private final LlmRouter llm;


    /**
     * Non-streaming chat reply
     */
    public String reply(String userEmail, String userMessage) {

        AppUser user = userService.getOrCreateDefault(userEmail);

        if (!user.isEnabled()) {
            return "⛔ User is disabled.";
        }

        UserRole role = user.getRole();

        if (!policyEngine.isAllowed(role, userMessage)) {
            return policyEngine.denyMessage(role);
        }

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

        if (!policyEngine.isAllowed(role, userMessage)) {
            onToken.accept(policyEngine.denyMessage(role));
            return;
        }

        // Stream from Ollama
        llm.stream(userMessage, onToken);
    }
}
