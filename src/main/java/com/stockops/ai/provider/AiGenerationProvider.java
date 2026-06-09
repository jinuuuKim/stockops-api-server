package com.stockops.ai.provider;

public interface AiGenerationProvider {

    String providerId();

    boolean isEnabled();

    AiGenerationResponse generate(AiGenerationRequest request);
}
