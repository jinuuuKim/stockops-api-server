package com.stockops.ai.gcp;

import com.stockops.ai.provider.AiGenerationProvider;
import com.stockops.ai.provider.AiGenerationRequest;
import com.stockops.ai.provider.AiGenerationResponse;
import com.stockops.ai.provider.AiServiceStatus;
import org.springframework.stereotype.Component;

@Component
public class VertexAiGenerationProvider implements AiGenerationProvider {

    private final VertexAiProperties properties;

    public VertexAiGenerationProvider(final VertexAiProperties properties) {
        this.properties = properties;
    }

    @Override
    public String providerId() {
        return "vertex-ai";
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Override
    public AiGenerationResponse generate(final AiGenerationRequest request) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Vertex AI provider is disabled");
        }
        if (properties.getProjectId() == null || properties.getProjectId().isBlank()) {
            throw new IllegalStateException("Vertex AI project id is not configured");
        }

        final String generatedText = callVertexAi(request);
        final String notice = request.chatVisible() ? properties.getFallbackNotice() : "";
        return new AiGenerationResponse(
                generatedText,
                providerId(),
                properties.getModelId(),
                AiServiceStatus.FALLBACK_ACTIVE,
                true,
                "BEDROCK_PROVIDER_UNAVAILABLE",
                notice,
                "");
    }

    private String callVertexAi(final AiGenerationRequest request) {
        final com.google.genai.Client client = com.google.genai.Client.builder()
                .vertexAI(true)
                .project(properties.getProjectId())
                .location(properties.getLocation())
                .build();
        final com.google.genai.types.GenerateContentResponse response =
                client.models.generateContent(
                        properties.getModelId(),
                        request.systemPrompt() + "\n\n" + request.userPrompt(),
                        null);
        final String text = response.text();
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("Vertex AI response was empty");
        }
        return text;
    }
}
