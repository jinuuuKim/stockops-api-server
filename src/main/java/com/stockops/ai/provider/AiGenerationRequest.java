package com.stockops.ai.provider;

public record AiGenerationRequest(
        String systemPrompt,
        String userPrompt,
        String useCase,
        boolean chatVisible) {
}
