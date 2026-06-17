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
import com.stockops.repository.AuditLogRepository;
import com.stockops.repository.ExpiryAlertRepository;
import com.stockops.repository.ProductRepository;
import com.stockops.repository.PurchaseOrderShipmentRepository;
import com.stockops.service.EnvironmentQueryService;
import com.stockops.service.ai.AIRecommendationService;
import com.stockops.service.ai.AISuggestionService;
import io.micrometer.observation.annotation.Observed;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
    /** Entity type simple names whose audit events are treated as privilege-sensitive (§3.2). */
    private static final List<String> PRIVILEGE_ENTITY_TYPES =
            List.of("User", "Role", "Permission", "RolePermission");

    private final AiProviderFacade providerFacade;
    private final BedrockPromptBuilder promptBuilder;
    private final BedrockAiProperties properties;
    private final BedrockAgentRuntimeClientAdapter agentAdapter;
    private final AIRecommendationService recommendationService;
    private final AISuggestionService aiSuggestionService;
    private final EnvironmentQueryService environmentQueryService;
    private final ExpiryAlertRepository expiryAlertRepository;
    private final PurchaseOrderShipmentRepository shipmentRepository;
    private final ProductRepository productRepository;
    private final AuditLogRepository auditLogRepository;

    public BedrockAiFacade(final AiProviderFacade providerFacade,
                           final BedrockPromptBuilder promptBuilder,
                           final BedrockAiProperties properties,
                           final BedrockAgentRuntimeClientAdapter agentAdapter,
                           final AIRecommendationService recommendationService,
                           final AISuggestionService aiSuggestionService,
                           final EnvironmentQueryService environmentQueryService,
                           final ExpiryAlertRepository expiryAlertRepository,
                           final PurchaseOrderShipmentRepository shipmentRepository,
                           final ProductRepository productRepository,
                           final AuditLogRepository auditLogRepository) {
        this.providerFacade = providerFacade;
        this.promptBuilder = promptBuilder;
        this.properties = properties;
        this.agentAdapter = agentAdapter;
        this.recommendationService = recommendationService;
        this.aiSuggestionService = aiSuggestionService;
        this.environmentQueryService = environmentQueryService;
        this.expiryAlertRepository = expiryAlertRepository;
        this.shipmentRepository = shipmentRepository;
        this.productRepository = productRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Cacheable(
            value = "ai::recommendation-explanation",
            key = "#recommendation.id()",
            condition = "#recommendation != null && #recommendation.id() != null",
            unless = "#result == null")
    @Observed(name = "ai.bedrock.explain_recommendation", contextualName = "bedrock-explain-recommendation")
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
    @Observed(name = "ai.bedrock.ops_summary", contextualName = "bedrock-ops-summary")
    public BedrockOpsSummaryResponse summarizeOperations(final LocalDate businessDate,
                                                         final Long centerId,
                                                         final Long warehouseId) {
        if (!properties.isEnabled()) {
            return new BedrockOpsSummaryResponse(
                    businessDate, centerId, warehouseId,
                    "AI 운영 요약 서비스가 비활성화 상태입니다.",
                    List.of(), List.of(), "LOW", Instant.now(),
                    Map.of(), "AI 서비스가 비활성화 상태입니다.");
        }
        final OpsFacts opsFacts = buildOpsFacts(businessDate, centerId, warehouseId);
        final String prompt = promptBuilder.buildOpsSummaryPrompt(opsFacts.factsJson());
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
                    List.of(), List.of(), "LOW", Instant.now(),
                    opsFacts.toSourceCounts(), "AI가 빈 응답을 반환하여 요약을 생성하지 못했습니다.");
        }
        return parseOpsSummaryResponse(responseText, businessDate, centerId, warehouseId,
                opsFacts.toSourceCounts(), opsFacts.buildConfidenceCaveat());
    }

    @Observed(name = "ai.bedrock.rag_query", contextualName = "bedrock-rag-query")
    public BedrockRagQueryResponse queryKnowledgeBase(final BedrockRagQueryRequest request) {
        return agentAdapter.retrieveAndGenerate(request);
    }

    @Observed(name = "ai.bedrock.agent_invoke", contextualName = "bedrock-agent-invoke")
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
     * Per §8 error policy: on parse failure, returns a safe deterministic fallback — raw model text
     * is never stored as-is (원문 일부를 저장하지 않고 안전한 fallback 생성).
     */
    private BedrockRecommendationExplanationResponse parseExplanationResponse(
            final String responseText,
            final AIRecommendationDTO recommendation,
            final String modelId) {
        try {
            final JsonNode json = JSON.readTree(extractJson(responseText));
            final String summary = json.has("summary")
                    ? json.get("summary").asText()
                    : "AI가 설명 요약을 제공하지 않았습니다.";
            final List<String> reasons = parseStringList(json, "reasons");
            final List<String> checklist = parseStringList(json, "reviewerChecklist");
            final String riskLevelStr = json.has("riskLevel")
                    ? normalizeRiskLevel(json.get("riskLevel").asText())
                    : riskLevel(recommendation);
            return new BedrockRecommendationExplanationResponse(
                    recommendation.id(), summary, reasons, checklist, riskLevelStr, modelId, Instant.now());
        } catch (final JsonProcessingException | IllegalArgumentException e) {
            log.warn("[Bedrock] Could not parse explanation JSON, returning safe fallback: {}", e.getMessage());
            return fallbackExplanation(recommendation, "응답 JSON 파싱 실패");
        }
    }

    /**
     * Parses a Bedrock ops summary response JSON.
     * Expected JSON fields: {@code summary}, {@code urgentItems}, {@code recommendedActions}, {@code riskLevel}.
     * Per §8 error policy: on parse failure, returns a safe deterministic message — raw model text
     * is never stored as-is (원문 일부를 저장하지 않고 안전한 fallback 생성).
     *
     * <p>{@code sourceCounts} and {@code confidenceCaveat} are passed in from the caller (computed
     * deterministically from the input data, independent of the AI response).
     */
    private BedrockOpsSummaryResponse parseOpsSummaryResponse(
            final String responseText,
            final LocalDate businessDate,
            final Long centerId,
            final Long warehouseId,
            final Map<String, Integer> sourceCounts,
            final String confidenceCaveat) {
        try {
            final JsonNode json = JSON.readTree(extractJson(responseText));
            final String summary = json.has("summary")
                    ? json.get("summary").asText()
                    : "AI가 운영 요약을 제공하지 않았습니다.";
            final List<String> urgentItems = parseStringList(json, "urgentItems");
            final List<String> recommendedActions = parseStringList(json, "recommendedActions");
            final String riskLevelStr = json.has("riskLevel")
                    ? normalizeRiskLevel(json.get("riskLevel").asText())
                    : "MEDIUM";
            return new BedrockOpsSummaryResponse(
                    businessDate, centerId, warehouseId,
                    summary, urgentItems, recommendedActions, riskLevelStr, Instant.now(),
                    sourceCounts, confidenceCaveat);
        } catch (final JsonProcessingException | IllegalArgumentException e) {
            log.warn("[Bedrock] Could not parse ops summary JSON, returning safe fallback: {}", e.getMessage());
            return new BedrockOpsSummaryResponse(
                    businessDate, centerId, warehouseId,
                    "AI 운영 요약 생성 중 오류가 발생했습니다.",
                    List.of(), List.of(), "MEDIUM", Instant.now(),
                    sourceCounts, confidenceCaveat);
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

    /**
     * Builds an enriched JSON facts payload for the Bedrock operations summary prompt.
     * Includes inventory recommendations, sensor alerts (7-day window), and expiry risk counts.
     * Returns an {@link OpsFacts} carrying both the JSON string and the source metadata
     * (used to compute {@code sourceCounts} and {@code confidenceCaveat} deterministically).
     */
    private OpsFacts buildOpsFacts(final LocalDate businessDate, final Long centerId, final Long warehouseId) {
        final List<AIRecommendationDTO> recommendations =
                recommendationService.listRecommendations(businessDate, centerId, warehouseId, null);

        // Sensor anomaly data — last 7 days
        List<Object> sensorAlerts = List.of();
        try {
            sensorAlerts = List.copyOf(environmentQueryService.getAlerts(7));
        } catch (final RuntimeException e) {
            log.warn("[OPS_FACTS] Could not load sensor alerts: {}", e.getMessage());
        }

        // Expiry risk counts by alert level
        long criticalExpiryCount = 0;
        long warningExpiryCount = 0;
        try {
            criticalExpiryCount = expiryAlertRepository.countByAlertLevelAndAcknowledgedFalse("CRITICAL");
            warningExpiryCount = expiryAlertRepository.countByAlertLevelAndAcknowledgedFalse("WARNING");
        } catch (final RuntimeException e) {
            log.warn("[OPS_FACTS] Could not load expiry alert counts: {}", e.getMessage());
        }

        // Overdue purchase order shipments (past ETA, not yet delivered)
        int overdueShipmentCount = 0;
        try {
            overdueShipmentCount = shipmentRepository
                    .findByEtaDateBeforeAndDeliveredAtIsNull(businessDate).size();
        } catch (final RuntimeException e) {
            log.warn("[OPS_FACTS] Could not load overdue shipment count: {}", e.getMessage());
        }

        // Products below safety stock threshold (global, unscoped — consistent with expiry/sensor counts)
        int inventoryBelowSafetyStockCount = 0;
        try {
            inventoryBelowSafetyStockCount = (int) productRepository.countProductsBelowSafetyStock();
        } catch (final RuntimeException e) {
            log.warn("[OPS_FACTS] Could not load inventory below safety stock count: {}", e.getMessage());
        }

        // Privilege-sensitive audit events (User, Role, Permission, RolePermission) — last 24 hours.
        // §3.2 candidate: 권한상 민감한 감사 로그 이벤트.
        // Note: "반복 실패" half of §3.2 requires an outcome/severity field not currently in
        // the AuditLog schema; only the "권한 변경" half is implemented here.
        int recentPrivilegeEventCount = 0;
        try {
            recentPrivilegeEventCount = (int) auditLogRepository
                    .countByEntityTypeInAndPerformedAtAfter(
                            PRIVILEGE_ENTITY_TYPES,
                            Instant.now().minus(24, ChronoUnit.HOURS));
        } catch (final RuntimeException e) {
            log.warn("[OPS_FACTS] Could not load privilege event count: {}", e.getMessage());
        }

        final int recCount = (int) Math.min(recommendations.size(), 20);
        final int alertCount = (int) Math.min(sensorAlerts.size(), 10);

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
        facts.put("sensorAlerts", sensorAlerts.stream().limit(10).toList());
        facts.put("expiryRisk", Map.of(
                "critical", criticalExpiryCount,
                "warning", warningExpiryCount));
        facts.put("overdueShipments", overdueShipmentCount);
        facts.put("inventoryBelowSafetyStock", inventoryBelowSafetyStockCount);
        facts.put("privilegeEvents", recentPrivilegeEventCount);

        String factsJson;
        try {
            factsJson = JSON.writeValueAsString(facts);
        } catch (final JsonProcessingException e) {
            log.warn("Failed to serialize ops facts: {}", e.getMessage());
            factsJson = "{}";
        }
        return new OpsFacts(factsJson, recCount, alertCount,
                (int) criticalExpiryCount, (int) warningExpiryCount, overdueShipmentCount,
                inventoryBelowSafetyStockCount, recentPrivilegeEventCount);
    }

    /**
     * Carries the serialized facts JSON and per-source counts for a single ops-summary call.
     * Source counts are used to build {@code sourceCounts} and {@code confidenceCaveat}
     * deterministically — they are never derived from the AI response (§8 policy).
     */
    private record OpsFacts(
            String factsJson,
            int recommendationCount,
            int sensorAlertCount,
            int criticalExpiryCount,
            int warningExpiryCount,
            int overdueShipmentCount,
            int inventoryBelowSafetyStockCount,
            int recentPrivilegeEventCount) {

        Map<String, Integer> toSourceCounts() {
            return Map.of(
                    "recommendations", recommendationCount,
                    "sensorAlerts", sensorAlertCount,
                    "criticalExpiry", criticalExpiryCount,
                    "warningExpiry", warningExpiryCount,
                    "overdueShipments", overdueShipmentCount,
                    "inventoryBelowSafetyStock", inventoryBelowSafetyStockCount,
                    "recentPrivilegeEvents", recentPrivilegeEventCount);
        }

        String buildConfidenceCaveat() {
            final int total = recommendationCount + sensorAlertCount
                    + criticalExpiryCount + warningExpiryCount + overdueShipmentCount
                    + inventoryBelowSafetyStockCount + recentPrivilegeEventCount;
            if (total < 5) {
                return "분석 가능한 데이터가 부족합니다. 더 많은 데이터가 확보되면 요약의 신뢰도가 높아집니다.";
            }
            return String.format(
                    "추천 %d건, 센서 알림 %d건, 만료 경보 %d건, 지연 PO %d건, 안전재고 미달 %d건, 권한 변경 %d건을 기반으로 생성되었습니다. " +
                    "실제 운영 결정 전 추가 검토를 권장합니다.",
                    recommendationCount, sensorAlertCount,
                    criticalExpiryCount + warningExpiryCount, overdueShipmentCount,
                    inventoryBelowSafetyStockCount, recentPrivilegeEventCount);
        }
    }
}
