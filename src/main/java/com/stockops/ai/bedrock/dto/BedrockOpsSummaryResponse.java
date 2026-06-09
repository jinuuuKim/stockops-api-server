package com.stockops.ai.bedrock.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record BedrockOpsSummaryResponse(
        LocalDate businessDate,
        Long centerId,
        Long warehouseId,
        String summary,
        List<String> urgentItems,
        List<String> recommendedActions,
        String riskLevel,
        Instant generatedAt) {
}
