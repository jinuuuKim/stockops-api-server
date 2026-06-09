package com.stockops.ai.bedrock;

import com.stockops.dto.AIRecommendationDTO;
import org.springframework.stereotype.Component;

@Component
public class BedrockPromptBuilder {

    public String buildRecommendationExplanationPrompt(final AIRecommendationDTO recommendation) {
        return """
                You are StockOps AI, an inventory operations assistant.
                Explain the reorder recommendation in Korean.
                Use only the facts in the JSON.
                Do not claim that the reorder is final approval.
                Return concise JSON with fields: summary, reasons, reviewerChecklist, riskLevel.

                Recommendation facts:
                {
                  "recommendationId": %d,
                  "businessDate": "%s",
                  "productId": %d,
                  "productName": "%s",
                  "centerId": %d,
                  "warehouseId": %d,
                  "status": "%s",
                  "currentStockQuantity": %d,
                  "safetyStockQuantity": %d,
                  "recommendedQuantity": %d,
                  "sevenDayForecastQuantity": %d,
                  "leadTimeDays": %d,
                  "leadTimeDemandQuantity": %d,
                  "demandEventCount": %d,
                  "insufficientHistory": %s,
                  "modelVersion": "%s"
                }
                """.formatted(
                recommendation.id(),
                recommendation.businessDate(),
                recommendation.productId(),
                sanitize(recommendation.productName()),
                recommendation.centerId(),
                recommendation.warehouseId(),
                recommendation.status(),
                recommendation.currentStockQuantity(),
                recommendation.safetyStockQuantity(),
                recommendation.recommendedQuantity(),
                recommendation.sevenDayForecastQuantity(),
                recommendation.leadTimeDays(),
                recommendation.leadTimeDemandQuantity(),
                recommendation.demandEventCount(),
                recommendation.insufficientHistory(),
                sanitize(recommendation.modelVersion()));
    }

    public String buildOpsSummaryPrompt(final String factsJson) {
        return """
                You are StockOps AI, an inventory operations assistant.
                Summarize operational anomalies in Korean.
                Use only the facts in the JSON.
                Return concise JSON with fields: summary, urgentItems, recommendedActions, riskLevel.

                Operation facts:
                %s
                """.formatted(factsJson);
    }

    private String sanitize(final String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
