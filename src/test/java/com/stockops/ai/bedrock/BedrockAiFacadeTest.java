package com.stockops.ai.bedrock;

import com.stockops.ai.bedrock.dto.BedrockAgentInvokeRequest;
import com.stockops.ai.bedrock.dto.BedrockAgentInvokeResponse;
import com.stockops.ai.provider.AiGenerationResponse;
import com.stockops.ai.provider.AiProviderFacade;
import com.stockops.ai.provider.AiServiceStatus;
import com.stockops.dto.AIRecommendationDTO;
import com.stockops.entity.ai.AIRecommendationStatus;
import com.stockops.service.ai.AIRecommendationService;
import com.stockops.service.ai.AISuggestionService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BedrockAiFacadeTest {

    @Mock AiProviderFacade providerFacade;
    @Mock BedrockPromptBuilder promptBuilder;
    @Mock BedrockAiProperties properties;
    @Mock BedrockAgentRuntimeClientAdapter agentAdapter;
    @Mock AIRecommendationService recommendationService;
    @Mock AISuggestionService aiSuggestionService;

    BedrockAiFacade facade;

    @BeforeEach
    void setUp() {
        facade = new BedrockAiFacade(providerFacade, promptBuilder, properties, agentAdapter,
                recommendationService, aiSuggestionService);
    }

    @Test
    void explainRecommendation_returnsFallbackWhenBedrockDisabled() {
        when(properties.isEnabled()).thenReturn(false);
        final AIRecommendationDTO dto = sampleDto();

        final var response = facade.explainRecommendation(dto);

        assertThat(response.modelId()).isEqualTo("fallback");
        assertThat(response.recommendationId()).isEqualTo(1L);
        assertThat(response.summary()).contains("50");
    }

    @Test
    void summarizeOperations_returnsPlaceholderWhenBedrockDisabled() {
        when(properties.isEnabled()).thenReturn(false);

        final var response = facade.summarizeOperations(LocalDate.of(2025, 6, 9), 1L, 1L);

        assertThat(response.summary()).contains("비활성화");
        assertThat(response.riskLevel()).isEqualTo("LOW");
    }

    @Test
    void explainRecommendation_parsesJsonFieldsFromBedrockResponse() {
        when(properties.isEnabled()).thenReturn(true);
        when(promptBuilder.buildRecommendationExplanationPrompt(any())).thenReturn("prompt");
        final String bedrockJson = """
                {"summary":"재고 부족 위험","reasons":["예측 초과","안전재고 이하"],
                "reviewerChecklist":["납기 확인"],"riskLevel":"HIGH"}
                """;
        when(providerFacade.generate(any())).thenReturn(
                new AiGenerationResponse(bedrockJson, "bedrock", "anthropic.claude-3-haiku",
                        AiServiceStatus.AVAILABLE, false, null, null, null, 80, 120));

        final var response = facade.explainRecommendation(sampleDto());

        assertThat(response.summary()).isEqualTo("재고 부족 위험");
        assertThat(response.reasons()).containsExactly("예측 초과", "안전재고 이하");
        assertThat(response.reviewerChecklist()).containsExactly("납기 확인");
        assertThat(response.riskLevel()).isEqualTo("HIGH");
        assertThat(response.modelId()).isEqualTo("anthropic.claude-3-haiku");
    }

    @Test
    void explainRecommendation_fallsBackToRawTextWhenJsonInvalid() {
        when(properties.isEnabled()).thenReturn(true);
        when(promptBuilder.buildRecommendationExplanationPrompt(any())).thenReturn("prompt");
        when(providerFacade.generate(any())).thenReturn(
                new AiGenerationResponse("plain text response (not JSON)", "bedrock",
                        "model", AiServiceStatus.AVAILABLE, false, null, null, null, null, null));

        final var response = facade.explainRecommendation(sampleDto());

        assertThat(response.summary()).isEqualTo("plain text response (not JSON)");
        assertThat(response.reasons()).isEmpty();
    }

    @Test
    void explainRecommendation_stripsMarkdownCodeFenceFromBedrockResponse() {
        when(properties.isEnabled()).thenReturn(true);
        when(promptBuilder.buildRecommendationExplanationPrompt(any())).thenReturn("prompt");
        final String fencedJson = """
                ```json
                {"summary":"요약","reasons":["근거"],"reviewerChecklist":[],"riskLevel":"MEDIUM"}
                ```""";
        when(providerFacade.generate(any())).thenReturn(
                new AiGenerationResponse(fencedJson, "bedrock", "model",
                        AiServiceStatus.AVAILABLE, false, null, null, null, null, null));

        final var response = facade.explainRecommendation(sampleDto());

        assertThat(response.summary()).isEqualTo("요약");
        assertThat(response.reasons()).containsExactly("근거");
        assertThat(response.riskLevel()).isEqualTo("MEDIUM");
    }

    @Test
    void summarizeOperations_parsesJsonFieldsFromBedrockResponse() {
        when(properties.isEnabled()).thenReturn(true);
        when(recommendationService.listRecommendations(any(), any(), any(), any())).thenReturn(java.util.List.of());
        when(promptBuilder.buildOpsSummaryPrompt(any())).thenReturn("prompt");
        final String bedrockJson = """
                {"summary":"운영 위험 높음","urgentItems":["재고 부족"],
                "recommendedActions":["즉시 발주"],"riskLevel":"HIGH"}
                """;
        when(providerFacade.generate(any())).thenReturn(
                new AiGenerationResponse(bedrockJson, "bedrock", "model",
                        AiServiceStatus.AVAILABLE, false, null, null, null, null, null));

        final var response = facade.summarizeOperations(LocalDate.of(2026, 6, 10), 1L, 1L);

        assertThat(response.summary()).isEqualTo("운영 위험 높음");
        assertThat(response.urgentItems()).containsExactly("재고 부족");
        assertThat(response.recommendedActions()).containsExactly("즉시 발주");
        assertThat(response.riskLevel()).isEqualTo("HIGH");
    }

    @Test
    void invokeAgent_createsAiSuggestionWhenActionSuggestedWithScope() {
        final BedrockAgentInvokeResponse agentResponse = new BedrockAgentInvokeResponse(
                "창고 2의 상품 A 재고 보충을 권장합니다.", "sess-1", true);
        when(agentAdapter.invokeAgent(any())).thenReturn(agentResponse);
        final ArgumentCaptor<AISuggestionService.CreateCommand> cmdCaptor =
                ArgumentCaptor.forClass(AISuggestionService.CreateCommand.class);

        facade.invokeAgent(new BedrockAgentInvokeRequest("재고 상태 분석", "sess-1", "WAREHOUSE", 2L));

        verify(aiSuggestionService).create(cmdCaptor.capture(), isNull(), any());
        final AISuggestionService.CreateCommand cmd = cmdCaptor.getValue();
        assertThat(cmd.source()).isEqualTo("BEDROCK_AGENT");
        assertThat(cmd.sourceType()).isEqualTo("AI_AGENT");
        assertThat(cmd.approvalMode()).isEqualTo("MANUAL_APPROVAL_REQUIRED");
        assertThat(cmd.targetScopeType()).isEqualTo("WAREHOUSE");
        assertThat(cmd.targetScopeId()).isEqualTo(2L);
    }

    @Test
    void invokeAgent_doesNotCreateSuggestionWhenActionNotSuggested() {
        when(agentAdapter.invokeAgent(any())).thenReturn(
                new BedrockAgentInvokeResponse("정보 제공만 합니다.", "sess-1", false));

        facade.invokeAgent(new BedrockAgentInvokeRequest("현황 문의", "sess-1", "WAREHOUSE", 2L));

        verify(aiSuggestionService, never()).create(any(), any(), any());
    }

    @Test
    void invokeAgent_doesNotCreateSuggestionWhenScopeIsMissing() {
        when(agentAdapter.invokeAgent(any())).thenReturn(
                new BedrockAgentInvokeResponse("조치 제안", null, true));

        facade.invokeAgent(new BedrockAgentInvokeRequest("현황 문의", null, null, null));

        verify(aiSuggestionService, never()).create(any(), any(), any());
    }

    private AIRecommendationDTO sampleDto() {
        return new AIRecommendationDTO(
                1L,
                LocalDate.of(2025, 6, 9),
                100L,
                "샘플 상품",
                "BAR-001",
                1L,
                1L,
                AIRecommendationStatus.READY_FOR_APPROVAL,
                10,
                5,
                50,
                48,
                3,
                15,
                BigDecimal.valueOf(7.5),
                BigDecimal.valueOf(8.0),
                BigDecimal.valueOf(7.8),
                30,
                false,
                null,
                null,
                null,
                null,
                null,
                "v2.0.0",
                Instant.parse("2025-06-09T00:00:00Z"),
                Instant.parse("2025-06-09T00:00:00Z"));
    }
}
