package com.aiguard.ai.gateway.guard.intent;

import org.springframework.stereotype.Component;
import java.util.Locale;

@Component
public class RuleBasedIntentClassifier implements IntentClassifier {
    public IntentClassification classify(String message) {
        String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (contains(text, "ignore previous", "system prompt", "jailbreak", "developer message")) return result(IntentType.PROMPT_INJECTION, "injection_pattern");
        if (contains(text, "password", "api key", "secret key", "access token")) return result(IntentType.SECRETS, "secret_pattern");
        if (contains(text, "vulnerability", "exploit", "malware", "credential stuffing")) return result(IntentType.SECURITY, "security_pattern");
        if (contains(text, "salary", "employee", "performance review", "human resources")) return result(IntentType.HR, "hr_pattern");
        if (contains(text, "invoice", "revenue", "budget", "financial", "bank account")) return result(IntentType.FINANCE, "finance_pattern");
        if (contains(text, "code", "deploy", "database", "api", "software")) return result(IntentType.ENGINEERING, "engineering_pattern");
        return new IntentClassification(IntentType.GENERAL, 1.0, "deterministic_default", "N/A");
    }
    private boolean contains(String text, String... terms) { for (String term : terms) if (text.contains(term)) return true; return false; }
    private IntentClassification result(IntentType intent, String reason) { return new IntentClassification(intent, 1.0, reason, "N/A"); }
}
