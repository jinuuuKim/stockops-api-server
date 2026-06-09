package com.stockops.ai.bedrock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stockops.ai.bedrock.dto.BedrockAgentInvokeRequest;
import com.stockops.ai.bedrock.dto.BedrockAgentInvokeResponse;
import com.stockops.ai.bedrock.dto.BedrockOpsSummaryResponse;
import com.stockops.ai.bedrock.dto.BedrockRagQueryRequest;
import com.stockops.ai.bedrock.dto.BedrockRagQueryResponse;
import com.stockops.ai.bedrock.dto.BedrockRecommendationExplanationResponse;
import com.stockops.ai.provider.AiGenerationRequest;
import com.stockops.ai.provider.AiGenerationResponse;
import com.stockops.ai.provider.AiProviderFacade;
import com.stockops.dto.AIRecommendationDTO;
import com.stockops.service.ai.AIRecommendationService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BedrockAiFacade {

    private static final Logger log = LoggerFactory.getLogger(BedrockAiFacade.class);
    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final AiProviderFacade providerFacade;
    private final BedrockPromptBuilder promptBuilder;
    private final BedrockAiProperties properties;
    private final BedrockAgentRuntimeClientAdapter agentAdapter;
    private final AIRecommendationService recommendationService;

    public BedrockAiFacade(final AiProviderFacade providerFacade,
                           final BedrockPromptBuilder promptBuilder,
                           final BedrockAiProperties properties,
                           final BedrockAgentRuntimeClientAdapter agentAdapter,
                           final AIRecommendationService recommendationService) {
        this.providerFacade = providerFacade;
        this.promptBuilder = promptBuilder;
        this.properties = properties;
        this.agentAdapter = agentAdapter;
        this.recommendationService = recommendationService;
    }

    public BedrockRecommendationExplanationResponse explainRecommendation(final AIRecommendationDTO recommendation) {
        if (!properties.isEnabled()) {
            return fallbackExplanation(recommendation, "Bedrock is disabled.");
        }
        final String prompt = promptBuilder.buildRecommendationExplanationPrompt(recommendation);
        final AiGenerationResponse generation = providerFacade.generate(new AiGenerationRequest(
                "You explain inventory recommendations. Return Korean JSON only.",
                prompt,
                "RECOMMENDATION_EXPLANATION",
                false));
        final String responseText = generation.text();
        if (responseText == null || responseText.isBlank()) {
            return fallbackExplanation(recommendation, "Bedrock response was empty.");
        }
        return new BedrockRecommendationExplanationResponse(
                recommendation.id(),
                responseText,
                List.of("Bedrock generated explanation from recommendation snapshot."),
                List.of("추천 수량 승인 전 공급 가능 여부를 확인하세요."),
                riskLevel(recommendation),
                generation.modelId(),
                Instant.now());
    }

    public BedrockOpsSummaryResponse summarizeOperations(final LocalDate businessDate,
                                                         final Long centerId,
                                                         final Long warehouseId) {
        if (!properties.isEnabled()) {
            return new BedrockOpsSummaryResponse(
                    businessDate, centerId, warehouseId,
                    "AI 운영 요약 서비스가 비활성화 상태입니다.",
                    List.of(), List.of(), "LOW", Instant.now());
        }
        final String factsJson = buildOpsFacts(businessDate, centerId, warehouseId);
        final String prompt = promptBuilder.buildOpsSummaryPrompt(factsJson);
        final AiGenerationResponse generation = providerFacade.generate(new AiGenerationRequest(
                "You summarize inventory operations. Return Korean JSON only.",
                prompt,
                "OPS_SUMMARY",
                false));
        final String responseText = generation.text();
        if (responseText == null || responseText.isBlank()) {
            return new BedrockOpsSummaryResponse(
                    businessDate, centerId, warehouseId,
                    "AI 운영 요약을 생성하지 못했습니다.",
                    List.of(), List.of(), "LOW", Instant.now());
        }
        return new BedrockOpsSummaryResponse(
                businessDate, centerId, warehouseId,
                responseText,
                List.of(), List.of(), "MEDIUM", Instant.now());
    }

    public BedrockRagQueryResponse queryKnowledgeBase(final BedrockRagQueryRequest request) {
        return agentAdapter.retrieveAndGenerate(request);
    }

    public BedrockAgentInvokeResponse invokeAgent(final BedrockAgentInvokeRequest request) {
        return agentAdapter.invokeAgent(request);
    }

    private BedrockRecommendationExplanationResponse fallbackExplanation(
            final AIRecommendationDTO recommendation, final String reason) {
        final String summary = "AI 설명을 생성하지 못했습니다. 추천 수량 "
                + recommendation.recommendedQuantity()
                + "개는 기존 예측 스냅샷 기준으로 계산되었습니다.";
        return new BedrockRecommendationExplanationResponse(
                recommendation.id(),
                summary,
                List.of(reason),
                List.of("예측 모델 버전과 최근 출고 이력을 확인하세요."),
                riskLevel(recommendation),
                "fallback",
                Instant.now());
    }

    private String riskLevel(final AIRecommendationDTO recommendation) {
        if (recommendation.recommendedQuantity() == null || recommendation.recommendedQuantity() <= 0) {
            return "LOW";
        }
        if (recommendation.currentStockQuantity() != null
                && recommendation.safetyStockQuantity() != null
                && recommendation.currentStockQuantity() < recommendation.safetyStockQuantity()) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    private String buildOpsFacts(final LocalDate businessDate, final Long centerId, final Long warehouseId) {
        final List<AIRecommendationDTO> recommendations =
                recommendationService.listRecommendations(businessDate, centerId, warehouseId, null);
        final Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("businessDate", businessDate);
        facts.put("centerId", centerId);
        facts.put("warehouseId", warehouseId);
        facts.put("recommendations", recommendations.stream()
                .limit(20)
                .map(r -> {
                    final Map<String, Object> item = new LinkedHashMap<>();
                    item.put("productId", r.productId());
                    item.put("recommendedQuantity", r.recommendedQuantity());
                    item.put("currentStockQuantity", r.currentStockQuantity());
                    item.put("safetyStockQuantity", r.safetyStockQuantity());
                    item.put("status", r.status());
                    return item;
                }).toList());
        try {
            return JSON.writeValueAsString(facts);
        } catch (final JsonProcessingException e) {
            log.warn("Failed to serialize ops facts: {}", e.getMessage());
            return "{}";
        }
    }
}
