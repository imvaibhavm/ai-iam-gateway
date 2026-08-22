package com.aiguard.ai.gateway.provider;

public record ProviderHealth(boolean available, String reason) {
    public static ProviderHealth up() { return new ProviderHealth(true, "available"); }
    public static ProviderHealth down(String reason) { return new ProviderHealth(false, reason); }
}
