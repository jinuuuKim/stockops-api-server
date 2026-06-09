package com.stockops.ai.bedrock;

import com.stockops.ai.provider.AiGenerationProvider;
import com.stockops.ai.provider.AiGenerationRequest;
import com.stockops.ai.provider.AiGenerationResponse;
import com.stockops.ai.provider.AiServiceStatus;
import java.util.List;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

@Component
public class BedrockGenerationProvider implements AiGenerationProvider {

    private final BedrockAiProperties properties;

    public BedrockGenerationProvider(final BedrockAiProperties properties) {
        this.properties = properties;
    }

    @Override
    public String providerId() {
        return "bedrock";
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Override
    public AiGenerationResponse generate(final AiGenerationRequest generationRequest) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Bedrock provider is disabled");
        }
        final String modelReference = properties.generationModelReference();
        if (modelReference == null || modelReference.isBlank()) {
            throw new IllegalStateException("Bedrock model reference is not configured");
        }

        try (BedrockRuntimeClient client = BedrockRuntimeClient.builder()
                .region(Region.of(properties.getRegion()))
                .build()) {
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
            return new AiGenerationResponse(
                    response.output().message().content().get(0).text(),
                    providerId(),
                    modelReference,
                    AiServiceStatus.AVAILABLE,
                    false,
                    "",
                    "",
                    "");
        }
    }
}
