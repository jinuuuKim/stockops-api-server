package com.stockops.ai.provider;

import com.stockops.ai.bedrock.BedrockGenerationProvider;
import com.stockops.ai.gcp.VertexAiGenerationProvider;
import com.stockops.ai.gcp.VertexAiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiProviderFacadeTest {

    @Mock BedrockGenerationProvider bedrockProvider;
    @Mock VertexAiGenerationProvider vertexProvider;
    @Mock VertexAiProperties vertexProperties;

    AiProviderFacade facade;

    @BeforeEach
    void setUp() {
        facade = new AiProviderFacade(bedrockProvider, vertexProvider, vertexProperties);
    }

    @Test
    void unconfiguredProvider_showsServiceNoticeForChatRequest() {
        when(bedrockProvider.isEnabled()).thenReturn(false);
        when(vertexProvider.isEnabled()).thenReturn(false);
        when(vertexProperties.getNoServiceNotice()).thenReturn("AI 서비스가 구성되지 않았습니다.");

        final AiGenerationResponse response = facade.generate(new AiGenerationRequest(
                "system", "hello", "CHAT", true));

        assertThat(response.serviceStatus()).isEqualTo(AiServiceStatus.UNCONFIGURED);
        assertThat(response.serviceNotice()).isEqualTo("AI 서비스가 구성되지 않았습니다.");
    }

    @Test
    void unconfiguredProvider_hidesServiceNoticeForRecommendationRequest() {
        when(bedrockProvider.isEnabled()).thenReturn(false);
        when(vertexProvider.isEnabled()).thenReturn(false);

        final AiGenerationResponse response = facade.generate(new AiGenerationRequest(
                "system", "explain", "RECOMMENDATION_EXPLANATION", false));

        assertThat(response.serviceStatus()).isEqualTo(AiServiceStatus.UNCONFIGURED);
        assertThat(response.serviceNotice()).isEqualTo("");
    }

    @Test
    void unauthenticatedProvider_showsServiceNoticeForChatRequest() {
        when(bedrockProvider.isEnabled()).thenReturn(true);
        when(bedrockProvider.generate(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("AccessDenied: missing credentials"));
        when(vertexProvider.isEnabled()).thenReturn(false);
        when(vertexProperties.getUnauthenticatedNotice()).thenReturn("AI 인증이 필요합니다.");

        final AiGenerationResponse response = facade.generate(new AiGenerationRequest(
                "system", "hello", "CHAT", true));

        assertThat(response.serviceStatus()).isEqualTo(AiServiceStatus.UNAUTHENTICATED);
        assertThat(response.serviceNotice()).isEqualTo("AI 인증이 필요합니다.");
    }

    @Test
    void unauthenticatedProvider_hidesServiceNoticeForNonChatRequest() {
        when(bedrockProvider.isEnabled()).thenReturn(true);
        when(bedrockProvider.generate(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("Unauthorized: credentials not found"));
        when(vertexProvider.isEnabled()).thenReturn(false);

        final AiGenerationResponse response = facade.generate(new AiGenerationRequest(
                "system", "explain", "RECOMMENDATION_EXPLANATION", false));

        assertThat(response.serviceStatus()).isEqualTo(AiServiceStatus.UNAUTHENTICATED);
        assertThat(response.serviceNotice()).isEqualTo("");
    }
}
