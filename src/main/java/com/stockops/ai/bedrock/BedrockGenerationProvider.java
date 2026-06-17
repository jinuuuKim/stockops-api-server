package com.stockops.ai.bedrock;

import com.stockops.ai.provider.AiGenerationProvider;
import com.stockops.ai.provider.AiGenerationRequest;
import com.stockops.ai.provider.AiGenerationResponse;
import com.stockops.ai.provider.AiServiceStatus;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

@Component
public class BedrockGenerationProvider implements AiGenerationProvider {

    private static final Logger log = LoggerFactory.getLogger(BedrockGenerationProvider.class);

    private final BedrockAiProperties properties;
    private final BedrockRuntimeClientFactory clientFactory;

    public BedrockGenerationProvider(final BedrockAiProperties properties,
                                     final BedrockRuntimeClientFactory clientFactory) {
        this.properties = properties;
        this.clientFactory = clientFactory;
    }

    @Override
    public String providerId() {
        return "bedrock";
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * Generates a response via the Bedrock Converse API.
     * Protected by a Resilience4j circuit breaker named "bedrock".
     * When the circuit is OPEN the fallback rethrows so {@code AiProviderFacade}
     * can attempt the Vertex AI fallback path.
     */
    @Override
    @CircuitBreaker(name = "bedrock", fallbackMethod = "circuitBreakerFallback")
    public AiGenerationResponse generate(final AiGenerationRequest generationRequest) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Bedrock provider is disabled");
        }
        final String modelReference = properties.generationModelReference();
        if (modelReference == null || modelReference.isBlank()) {
            throw new IllegalStateException("Bedrock model reference is not configured");
        }

        try (BedrockRuntimeClient client = clientFactory.create(properties.getRegion())) {
            final ConverseRequest request = ConverseRequest.builder()
                    .modelId(modelReference)
                    .system(List.of(SystemContentBlock.builder().text(generationRequest.systemPrompt()).build()))
                    .messages(Message.builder()
                            .role(ConversationRole.USER)
                            .content(ContentBlock.builder().text(generationRequest.userPrompt()).build())
                            .build())
                    .build();
            final ConverseResponse response = client.converse(request);
            if (response.output() == null
                    || response.output().message() == null
                    || response.output().message().content().isEmpty()) {
                throw new IllegalStateException("Bedrock response was empty");
            }
            final TokenUsage usage = response.usage();
            return new AiGenerationResponse(
                    response.output().message().content().get(0).text(),
                    providerId(),
                    modelReference,
                    AiServiceStatus.AVAILABLE,
                    false,
                    "",
                    "",
                    "",
                    usage != null ? usage.inputTokens() : null,
                    usage != null ? usage.outputTokens() : null);
        }
    }

    /**
     * Circuit breaker fallback — invoked when the "bedrock" circuit is OPEN
     * or when the call raises an exception after the sliding window threshold.
     * Rethrows as a RuntimeException so {@link com.stockops.ai.provider.AiProviderFacade}
     * can delegate to the Vertex AI fallback provider.
     *
     * @param request original generation request
     * @param cause   the exception that opened (or was recorded by) the circuit breaker
     */
    @SuppressWarnings("unused")
    public AiGenerationResponse circuitBreakerFallback(
            final AiGenerationRequest request, final Exception cause) {
        log.warn("[Bedrock] Circuit breaker intercepted — cause: {}", cause.getMessage());
        throw new RuntimeException("Bedrock circuit breaker open: " + cause.getMessage(), cause);
    }
}
