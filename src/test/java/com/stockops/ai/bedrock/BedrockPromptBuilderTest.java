package com.stockops.ai.bedrock;

import com.stockops.dto.AIRecommendationDTO;
import com.stockops.entity.ai.AIRecommendationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BedrockPromptBuilderTest {

    private final BedrockPromptBuilder builder = new BedrockPromptBuilder();

    @Test
    void buildRecommendationExplanationPrompt_containsKeyFacts() {
        final AIRecommendationDTO dto = sampleDto(1L, "테스트 상품", "v2.0.0");

        final String prompt = builder.buildRecommendationExplanationPrompt(dto);

        assertThat(prompt).contains("테스트 상품");
        assertThat(prompt).contains("\"recommendedQuantity\": 50");
        assertThat(prompt).contains("\"currentStockQuantity\": 10");
        assertThat(prompt).contains("v2.0.0");
    }

    @Test
    void buildRecommendationExplanationPrompt_sanitizesDoubleQuotes() {
        final AIRecommendationDTO dto = sampleDto(2L, "품명 \"특수\"", "v1");

        final String prompt = builder.buildRecommendationExplanationPrompt(dto);

        assertThat(prompt).doesNotContain("\"특수\"");
        assertThat(prompt).contains("\\\"특수\\\"");
    }

    @Test
    void buildOpsSummaryPrompt_containsFactsJson() {
        final String facts = "{\"businessDate\":\"2025-06-09\",\"recommendations\":[]}";

        final String prompt = builder.buildOpsSummaryPrompt(facts);

        assertThat(prompt).contains(facts);
        assertThat(prompt).contains("urgentItems");
        assertThat(prompt).contains("Korean");
    }

    private AIRecommendationDTO sampleDto(final Long id, final String name, final String modelVersion) {
        return new AIRecommendationDTO(
                id,
                LocalDate.of(2025, 6, 9),
                100L,
                name,
                "BARCODE-001",
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
                modelVersion,
                Instant.parse("2025-06-09T00:00:00Z"),
                Instant.parse("2025-06-09T00:00:00Z"));
    }
}
