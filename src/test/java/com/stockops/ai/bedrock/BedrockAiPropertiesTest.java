package com.stockops.ai.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BedrockAiPropertiesTest {

    @Test
    void generationModelReferencePrefersInferenceProfileArn() {
        final BedrockAiProperties properties = new BedrockAiProperties();
        properties.setModelId("anthropic.claude-3-5-sonnet-20241022-v2:0");
        properties.setInferenceProfileArn("arn:aws:bedrock:ap-northeast-2:123456789012:inference-profile/example");

        assertThat(properties.generationModelReference())
                .isEqualTo("arn:aws:bedrock:ap-northeast-2:123456789012:inference-profile/example");
    }

    @Test
    void generationModelReferenceFallsBackToModelId() {
        final BedrockAiProperties properties = new BedrockAiProperties();
        properties.setModelId("amazon.nova-lite-v1:0");

        assertThat(properties.generationModelReference()).isEqualTo("amazon.nova-lite-v1:0");
    }

    @Test
    void generationModelReferenceReturnsBlanksWhenNeitherConfigured() {
        final BedrockAiProperties properties = new BedrockAiProperties();

        assertThat(properties.generationModelReference()).isBlank();
    }
}
