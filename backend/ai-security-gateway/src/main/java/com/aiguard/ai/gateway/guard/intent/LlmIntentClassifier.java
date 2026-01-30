package com.aiguard.ai.gateway.guard.intent;

import com.aiguard.ai.gateway.llm.LlmRouter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LlmIntentClassifier implements IntentClassifier {

    private final LlmRouter llm;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public IntentClassification classify(String userMessage) {

        if (userMessage == null) userMessage = "";

        String prompt = """
                You are an enterprise AI security intent classifier.

                Task:
                Classify the user's message into exactly ONE of these intents:
                GENERAL, FINANCE, HR, ENGINEERING, SECURITY, SECRETS, PII, PROMPT_INJECTION

                Rules:
                - Return ONLY valid JSON
                - No markdown, no explanation outside JSON
                - confidence must be a number 0 to 1

                JSON format:
                {"intent":"GENERAL","confidence":0.0,"reason":"short reason"}

                User message:
                %s
                """.formatted(userMessage);

        String raw = llm.generate(prompt);

        // safe fallback
        IntentClassification fallback =
                new IntentClassification(IntentType.GENERAL, 0.40, "fallback", raw);

        try {
            // Sometimes models wrap output with extra text; try to extract JSON
            String jsonStr = extractJson(raw);
            JsonNode root = mapper.readTree(jsonStr);

            String intentStr = root.path("intent").asText("GENERAL").trim();
            double confidence = root.path("confidence").asDouble(0.50);
            String reason = root.path("reason").asText("n/a");

            IntentType intent;
            try {
                intent = IntentType.valueOf(intentStr);
            } catch (Exception ex) {
                intent = IntentType.GENERAL;
            }

            return new IntentClassification(intent, confidence, reason, raw);

        } catch (Exception e) {
            return fallback;
        }
    }

    // Extract first {...} JSON object from raw text
    private String extractJson(String raw) {
        if (raw == null) return "{}";
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw.trim();
    }
}
