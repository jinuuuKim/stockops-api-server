package com.stockops.ai.bedrock;

import com.stockops.ai.provider.AiProviderFacade;
import com.stockops.dto.AIRecommendationDTO;
import com.stockops.entity.ai.AIRecommendationStatus;
import com.stockops.service.ai.AIRecommendationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BedrockAiFacadeTest {

    @Mock AiProviderFacade providerFacade;
    @Mock BedrockPromptBuilder promptBuilder;
    @Mock BedrockAiProperties properties;
    @Mock BedrockAgentRuntimeClientAdapter agentAdapter;
    @Mock AIRecommendationService recommendationService;

    BedrockAiFacade facade;

    @BeforeEach
    void setUp() {
        facade = new BedrockAiFacade(providerFacade, promptBuilder, properties, agentAdapter, recommendationService);
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
