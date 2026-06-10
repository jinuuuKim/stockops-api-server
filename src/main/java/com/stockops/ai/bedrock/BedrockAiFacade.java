package com.stockops.ai.bedrock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
import com.stockops.service.ai.AISuggestionService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
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
    private final AISuggestionService aiSuggestionService;

    public BedrockAiFacade(final AiProviderFacade providerFacade,
                           final BedrockPromptBuilder promptBuilder,
                           final BedrockAiProperties properties,
                           final BedrockAgentRuntimeClientAdapter agentAdapter,
                           final AIRecommendationService recommendationService,
                           final AISuggestionService aiSuggestionService) {
        this.providerFacade = providerFacade;
        this.promptBuilder = promptBuilder;
        this.properties = properties;
        this.agentAdapter = agentAdapter;
        this.recommendationService = recommendationService;
        this.aiSuggestionService = aiSuggestionService;
    }

    @Cacheable(
            value = "ai::recommendation-explanation",
            key = "#recommendation.id()",
            condition = "#recommendation != null && #recommendation.id() != null",
            unless = "#result == null")
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
        return parseExplanationResponse(responseText, recommendation, generation.modelId());
    }

    @Cacheable(
            value = "ai::ops-summary",
            key = "#businessDate.toString() + '-' + (#centerId ?: 'all') + '-' + (#warehouseId ?: 'all')",
            condition = "#businessDate != null",
            unless = "#result == null")
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
        return parseOpsSummaryResponse(responseText, businessDate, centerId, warehouseId);
    }

    public BedrockRagQueryResponse queryKnowledgeBase(final BedrockRagQueryRequest request) {
        return agentAdapter.retrieveAndGenerate(request);
    }

    public BedrockAgentInvokeResponse invokeAgent(final BedrockAgentInvokeRequest request) {
        final BedrockAgentInvokeResponse response = agentAdapter.invokeAgent(request);
        if (response.actionSuggested()
                && request.targetScopeType() != null && !request.targetScopeType().isBlank()
                && request.targetScopeId() != null) {
            try {
                aiSuggestionService.create(buildAgentSuggestionCommand(request, response), null,
                        UUID.randomUUID().toString());
            } catch (final RuntimeException e) {
                log.warn("Failed to create AISuggestion from agent response: {}", e.getMessage());
            }
        }
        return response;
    }

    private AISuggestionService.CreateCommand buildAgentSuggestionCommand(
            final BedrockAgentInvokeRequest request, final BedrockAgentInvokeResponse response) {
        final String title = response.answer() != null && response.answer().length() > 80
                ? response.answer().substring(0, 80) + "..."
                : (response.answer() != null ? response.answer() : "Bedrock Agent 제안");
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", response.sessionId());
        payload.put("answer", response.answer());
        String payloadJson;
        try {
            payloadJson = JSON.writeValueAsString(payload);
        } catch (final JsonProcessingException e) {
            payloadJson = "{}";
        }
        return new AISuggestionService.CreateCommand(
                "AGENT_SUGGESTION",
                "MEDIUM",
                title,
                response.answer(),
                "Bedrock Agent 제안",
                response.answer(),
                null,
                null,
                request.targetScopeType(),
                request.targetScopeId(),
                payloadJson,
                null,
                "BEDROCK_AGENT",
                "AI_AGENT",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "MANUAL_APPROVAL_REQUIRED",
                null,
                null,
                null,
                null);
    }

    /**
     * Parses a Bedrock explanation response JSON into a structured response.
     * Expected JSON fields: {@code summary}, {@code reasons}, {@code reviewerChecklist}, {@code riskLevel}.
     * Falls back gracefully if the response is not valid JSON or fields are missing.
     */
    private BedrockRecommendationExplanationResponse parseExplanationResponse(
            final String responseText,
            final AIRecommendationDTO recommendation,
            final String modelId) {
        try {
            final JsonNode json = JSON.readTree(extractJson(responseText));
            final String summary = json.has("summary") ? json.get("summary").asText() : responseText;
            final List<String> reasons = parseStringList(json, "reasons");
            final List<String> checklist = parseStringList(json, "reviewerChecklist");
            final String riskLevelStr = json.has("riskLevel")
                    ? normalizeRiskLevel(json.get("riskLevel").asText())
                    : riskLevel(recommendation);
            return new BedrockRecommendationExplanationResponse(
                    recommendation.id(), summary, reasons, checklist, riskLevelStr, modelId, Instant.now());
        } catch (final JsonProcessingException | IllegalArgumentException e) {
            log.warn("[Bedrock] Could not parse explanation JSON, using raw text as summary: {}", e.getMessage());
            return new BedrockRecommendationExplanationResponse(
                    recommendation.id(),
                    responseText,
                    List.of(),
                    List.of("추천 수량 승인 전 공급 가능 여부를 확인하세요."),
                    riskLevel(recommendation),
                    modelId,
                    Instant.now());
        }
    }

    /**
     * Parses a Bedrock ops summary response JSON.
     * Expected JSON fields: {@code summary}, {@code urgentItems}, {@code recommendedActions}, {@code riskLevel}.
     */
    private BedrockOpsSummaryResponse parseOpsSummaryResponse(
            final String responseText,
            final LocalDate businessDate,
            final Long centerId,
            final Long warehouseId) {
        try {
            final JsonNode json = JSON.readTree(extractJson(responseText));
            final String summary = json.has("summary") ? json.get("summary").asText() : responseText;
            final List<String> urgentItems = parseStringList(json, "urgentItems");
            final List<String> recommendedActions = parseStringList(json, "recommendedActions");
            final String riskLevelStr = json.has("riskLevel")
                    ? normalizeRiskLevel(json.get("riskLevel").asText())
                    : "MEDIUM";
            return new BedrockOpsSummaryResponse(
                    businessDate, centerId, warehouseId,
                    summary, urgentItems, recommendedActions, riskLevelStr, Instant.now());
        } catch (final JsonProcessingException | IllegalArgumentException e) {
            log.warn("[Bedrock] Could not parse ops summary JSON, using raw text: {}", e.getMessage());
            return new BedrockOpsSummaryResponse(
                    businessDate, centerId, warehouseId,
                    responseText, List.of(), List.of(), "MEDIUM", Instant.now());
        }
    }

    /**
     * Extracts a JSON object from the model response. Some models wrap JSON in markdown code fences;
     * this strips the fence if present.
     */
    private static String extractJson(final String text) {
        final String trimmed = text.strip();
        // Strip ```json ... ``` or ``` ... ``` fences
        if (trimmed.startsWith("```")) {
            final int firstNewline = trimmed.indexOf('\n');
            final int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).strip();
            }
        }
        return trimmed;
    }

    private static List<String> parseStringList(final JsonNode json, final String field) {
        if (!json.has(field) || !json.get(field).isArray()) {
            return List.of();
        }
        final List<String> items = new java.util.ArrayList<>();
        json.get(field).forEach(node -> {
            if (!node.isNull()) {
                items.add(node.asText());
            }
        });
        return List.copyOf(items);
    }

    private static String normalizeRiskLevel(final String raw) {
        return switch (raw.toUpperCase(java.util.Locale.ROOT)) {
            case "HIGH", "높음", "높다" -> "HIGH";
            case "MEDIUM", "MODERATE", "보통", "중간" -> "MEDIUM";
            default -> "LOW";
        };
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
