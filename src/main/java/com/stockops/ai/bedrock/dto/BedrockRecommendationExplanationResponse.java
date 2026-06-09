package com.stockops.ai.bedrock.dto;

import java.time.Instant;
import java.util.List;

public record BedrockRecommendationExplanationResponse(
        Long recommendationId,
        String summary,
        List<String> reasons,
        List<String> reviewerChecklist,
        String riskLevel,
        String modelId,
        Instant generatedAt) {
}
