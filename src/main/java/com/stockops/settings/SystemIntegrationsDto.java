package com.stockops.settings;

public record SystemIntegrationsDto(
        BedrockIntegration bedrock,
        VertexIntegration vertex,
        GeminiIntegration gemini
) {

    public record BedrockIntegration(
            boolean enabled,
            String region,
            String modelReference,
            boolean hasKnowledgeBase,
            boolean hasAgent
    ) {}

    public record VertexIntegration(
            boolean enabled,
            String location,
            String modelId,
            boolean hasCredentials
    ) {}

    public record GeminiIntegration(
            boolean enabled,
            String modelName,
            boolean hasApiKey
    ) {}
}
