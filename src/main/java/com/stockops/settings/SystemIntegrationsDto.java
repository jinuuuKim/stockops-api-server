package com.stockops.settings;

public record SystemIntegrationsDto(
        BedrockIntegration bedrock,
        VertexIntegration vertex
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
}
