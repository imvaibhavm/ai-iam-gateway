package com.aiguard.ai.gateway.guard.intent;

public interface IntentClassifier {
    IntentClassification classify(String userMessage);
}
