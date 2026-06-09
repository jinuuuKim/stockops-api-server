package com.stockops.ai.provider;

import com.stockops.ai.bedrock.BedrockGenerationProvider;
import com.stockops.ai.gcp.VertexAiGenerationProvider;
import com.stockops.ai.gcp.VertexAiProperties;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class AiProviderFacade {

    private final BedrockGenerationProvider bedrockProvider;
    private final VertexAiGenerationProvider vertexProvider;
    private final VertexAiProperties vertexProperties;

    public AiProviderFacade(final BedrockGenerationProvider bedrockProvider,
                            final VertexAiGenerationProvider vertexProvider,
                            final VertexAiProperties vertexProperties) {
        this.bedrockProvider = bedrockProvider;
        this.vertexProvider = vertexProvider;
        this.vertexProperties = vertexProperties;
    }

    public AiGenerationResponse generate(final AiGenerationRequest request) {
        if (!bedrockProvider.isEnabled() && !vertexProvider.isEnabled()) {
            return noConfiguredProvider(request);
        }
        try {
            if (bedrockProvider.isEnabled()) {
                return bedrockProvider.generate(request);
            }
            throw new IllegalStateException("Bedrock provider is disabled");
        } catch (final RuntimeException bedrockFailure) {
            if (isAuthenticationFailure(bedrockFailure) && !vertexProvider.isEnabled()) {
                return unauthenticated(request);
            }
            if (!vertexProvider.isEnabled()) {
                throw bedrockFailure;
            }
            try {
                return vertexProvider.generate(request);
            } catch (final RuntimeException vertexFailure) {
                if (isAuthenticationFailure(vertexFailure)) {
                    return unauthenticated(request);
                }
                throw vertexFailure;
            }
        }
    }

    private AiGenerationResponse noConfiguredProvider(final AiGenerationRequest request) {
        return new AiGenerationResponse(
                "",
                "none",
                "",
                AiServiceStatus.UNCONFIGURED,
                false,
                "AI_SERVICE_UNCONFIGURED",
                "",
                request.chatVisible() ? vertexProperties.getNoServiceNotice() : "");
    }

    private AiGenerationResponse unauthenticated(final AiGenerationRequest request) {
        return new AiGenerationResponse(
                "",
                "none",
                "",
                AiServiceStatus.UNAUTHENTICATED,
                false,
                "AI_SERVICE_UNAUTHENTICATED",
                "",
                request.chatVisible() ? vertexProperties.getUnauthenticatedNotice() : "");
    }

    private boolean isAuthenticationFailure(final RuntimeException failure) {
        final String message = failure.getMessage() == null ? "" : failure.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("accessdenied")
                || message.contains("unauthorized")
                || message.contains("unauthenticated")
                || message.contains("permission denied")
                || message.contains("credentials");
    }
}
