package com.stockops.ai.bedrock.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stockops.ai.forecast.AiForecastClient;
import com.stockops.ai.forecast.ForecastModel;
import com.stockops.dto.AIRecommendationDTO;
import com.stockops.dto.analytics.AnalyticsQueryFilter;
import com.stockops.entity.ExpiryAlert;
import com.stockops.entity.PurchaseOrderShipment;
import com.stockops.entity.ai.AISuggestion;
import com.stockops.repository.ExpiryAlertRepository;
import com.stockops.repository.PurchaseOrderShipmentRepository;
import com.stockops.report.InventoryTurnoverReportService;
import com.stockops.service.AbcXyzReportService;
import com.stockops.service.CenterInventoryAggregationService;
import com.stockops.service.EnvironmentQueryService;
import com.stockops.service.InventoryQueryService;
import com.stockops.service.SensorReadingQueryService;
import com.stockops.service.ai.AIRecommendationService;
import com.stockops.service.ai.AISuggestionService;
import com.stockops.service.analytics.AnalyticsReportingService;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dispatches Bedrock Agent tool (return-control) calls to the appropriate StockOps service.
 *
 * <p>Supported tools:
 * <ul>
 *   <li>{@code getInventoryRisk} — returns inventory snapshot for a product or all products</li>
 *   <li>{@code getForecastRecommendation} — returns AI recommendations for a given date/scope</li>
 *   <li>{@code getSensorAnomalies} — returns recent sensor alerts</li>
 *   <li>{@code getPurchaseOrderDelaySummary} — returns overdue shipments with days-overdue field</li>
 *   <li>{@code getProphetForecast} — runs an on-demand Prophet demand forecast for a product</li>
 *   <li>{@code createAISuggestionDraft} — creates a PENDING AISuggestion for human review</li>
 * </ul>
 *
 * @author StockOps Team
 * @since 2.0
 */
@Component
public class AgentToolDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AgentToolDispatcher.class);

    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final int DEFAULT_FORECAST_DAYS = 7;
    private static final int MAX_FORECAST_DAYS = 30;
    private static final int DEFAULT_EXPIRY_DAYS = 30;
    private static final int MAX_LIST_RESULTS = 50;
    private static final int DEFAULT_TRANSACTION_LIMIT = 20;

    private final InventoryQueryService inventoryQueryService;
    private final AIRecommendationService recommendationService;
    private final EnvironmentQueryService environmentQueryService;
    private final AISuggestionService aiSuggestionService;
    private final PurchaseOrderShipmentRepository shipmentRepository;
    private final AiForecastClient aiForecastClient;
    private final SensorReadingQueryService sensorReadingQueryService;
    private final ExpiryAlertRepository expiryAlertRepository;
    private final CenterInventoryAggregationService centerInventoryAggregationService;
    private final AbcXyzReportService abcXyzReportService;
    private final InventoryTurnoverReportService inventoryTurnoverReportService;
    private final AnalyticsReportingService analyticsReportingService;

    public AgentToolDispatcher(final InventoryQueryService inventoryQueryService,
                               final AIRecommendationService recommendationService,
                               final EnvironmentQueryService environmentQueryService,
                               final AISuggestionService aiSuggestionService,
                               final PurchaseOrderShipmentRepository shipmentRepository,
                               final AiForecastClient aiForecastClient,
                               final SensorReadingQueryService sensorReadingQueryService,
                               final ExpiryAlertRepository expiryAlertRepository,
                               final CenterInventoryAggregationService centerInventoryAggregationService,
                               final AbcXyzReportService abcXyzReportService,
                               final InventoryTurnoverReportService inventoryTurnoverReportService,
                               final AnalyticsReportingService analyticsReportingService) {
        this.inventoryQueryService = inventoryQueryService;
        this.recommendationService = recommendationService;
        this.environmentQueryService = environmentQueryService;
        this.aiSuggestionService = aiSuggestionService;
        this.shipmentRepository = shipmentRepository;
        this.aiForecastClient = aiForecastClient;
        this.sensorReadingQueryService = sensorReadingQueryService;
        this.expiryAlertRepository = expiryAlertRepository;
        this.centerInventoryAggregationService = centerInventoryAggregationService;
        this.abcXyzReportService = abcXyzReportService;
        this.inventoryTurnoverReportService = inventoryTurnoverReportService;
        this.analyticsReportingService = analyticsReportingService;
    }

    /**
     * Dispatches an agent tool call.
     * The method is read-only transactional so lazy-loaded relationships (e.g. shipment → PO)
     * can be accessed safely within the scope of a single transaction.
     *
     * @param toolName  the agent-specified tool name
     * @param inputJson JSON string containing tool arguments from the agent
     * @return result of the tool invocation
     */
    @Transactional(readOnly = true)
    public AgentToolResult dispatch(final String toolName, final String inputJson) {
        log.debug("[Agent] Dispatching tool: {} input={}", toolName, inputJson);
        try {
            final JsonNode input = JSON.readTree(inputJson != null ? inputJson : "{}");
            return switch (toolName) {
                case "searchInventory" -> handleSearchInventory(input);
                case "getInventoryRisk" -> handleInventoryRisk(input);
                case "getForecastRecommendation" -> handleForecastRecommendation(input);
                case "getSensorAnomalies" -> handleSensorAnomalies(input);
                case "getPurchaseOrderDelaySummary" -> handlePurchaseOrderDelaySummary(input);
                case "getProphetForecast" -> handleProphetForecast(input);
                case "getRecentSensorReadings" -> handleRecentSensorReadings(input);
                case "getExpiringLots" -> handleExpiringLots(input);
                case "getInventoryByLocation" -> handleInventoryByLocation(input);
                case "getRecommendationExplanationContext" -> handleRecommendationExplanationContext(input);
                case "getCenterInventorySummary" -> handleCenterInventorySummary(input);
                case "getInventoryTransactionHistory" -> handleInventoryTransactionHistory(input);
                case "getAbcXyzClassification" -> handleAbcXyzClassification(input);
                case "getInventoryTurnover" -> handleInventoryTurnover(input);
                case "getFillRate" -> handleFillRate(input);
                case "getExpiryWaste" -> handleExpiryWaste(input);
                case "generateRecommendationSnapshot" -> handleGenerateRecommendationSnapshot(input);
                case "createAISuggestionDraft" -> handleCreateAISuggestionDraft(input);
                default -> {
                    log.warn("[Agent] Unknown tool: {}", toolName);
                    yield AgentToolResult.failure(toolName, "Unknown tool: " + toolName);
                }
            };
        } catch (final Exception e) {
            log.error("[Agent] Tool dispatch failed for {}: {}", toolName, e.getMessage(), e);
            return AgentToolResult.failure(toolName, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Tool handlers
    // -------------------------------------------------------------------------

    private AgentToolResult handleSearchInventory(final JsonNode input) throws JsonProcessingException {
        final String query = text(input, "query", null);
        if (query == null || query.isBlank()) {
            return AgentToolResult.failure("searchInventory", "query is required");
        }
        final Object result = inventoryQueryService.searchInventory(query);
        return AgentToolResult.success("searchInventory", JSON.writeValueAsString(result));
    }

    private AgentToolResult handleInventoryRisk(final JsonNode input) throws JsonProcessingException {
        final Long productId = longOrNull(input, "productId");
        final Object result;
        if (productId != null) {
            result = inventoryQueryService.getInventoryByProduct(productId);
        } else {
            result = inventoryQueryService.getAllInventory();
        }
        return AgentToolResult.success("getInventoryRisk", JSON.writeValueAsString(result));
    }

    private AgentToolResult handleForecastRecommendation(final JsonNode input) throws JsonProcessingException {
        final LocalDate businessDate = input.has("businessDate")
                ? LocalDate.parse(input.get("businessDate").asText())
                : LocalDate.now();
        final Long centerId = longOrNull(input, "centerId");
        final Long warehouseId = longOrNull(input, "warehouseId");
        final List<AIRecommendationDTO> recommendations =
                recommendationService.listRecommendations(businessDate, centerId, warehouseId, null);
        return AgentToolResult.success("getForecastRecommendation", JSON.writeValueAsString(recommendations));
    }

    private AgentToolResult handleSensorAnomalies(final JsonNode input) throws JsonProcessingException {
        final int days = input.has("days") ? input.get("days").asInt(7) : 7;
        final Object alerts = environmentQueryService.getAlerts(days);
        return AgentToolResult.success("getSensorAnomalies", JSON.writeValueAsString(alerts));
    }

    private AgentToolResult handlePurchaseOrderDelaySummary(final JsonNode input) throws JsonProcessingException {
        final LocalDate today = LocalDate.now();
        final List<PurchaseOrderShipment> overdueShipments =
                shipmentRepository.findByEtaDateBeforeAndDeliveredAtIsNull(today);

        final List<Map<String, Object>> summary = overdueShipments.stream()
                .map(s -> {
                    final Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("shipmentId", s.getId());
                    entry.put("shipmentNumber", s.getShipmentNumber());
                    entry.put("poNumber", s.getPurchaseOrder() != null ? s.getPurchaseOrder().getPoNumber() : null);
                    entry.put("carrier", s.getCarrier());
                    entry.put("etaDate", s.getEtaDate() != null ? s.getEtaDate().toString() : null);
                    entry.put("daysOverdue", s.getEtaDate() != null
                            ? (int) (today.toEpochDay() - s.getEtaDate().toEpochDay()) : null);
                    return entry;
                })
                .toList();

        return AgentToolResult.success("getPurchaseOrderDelaySummary", JSON.writeValueAsString(summary));
    }

    private AgentToolResult handleProphetForecast(final JsonNode input) throws JsonProcessingException {
        final Long productId = longOrNull(input, "productId");
        if (productId == null) {
            return AgentToolResult.failure("getProphetForecast", "productId is required");
        }
        final int requestedDays = input.has("days") ? input.get("days").asInt(DEFAULT_FORECAST_DAYS)
                : DEFAULT_FORECAST_DAYS;
        final int days = Math.min(Math.max(requestedDays, 1), MAX_FORECAST_DAYS);

        final AiForecastClient.AiForecastResponse forecast = aiForecastClient.getForecast(productId, days);
        if (forecast == null) {
            return AgentToolResult.failure("getProphetForecast",
                    "Prophet 예측 서비스를 사용할 수 없습니다. 통계 모델 기반 추천은 getForecastRecommendation으로 조회하세요.");
        }

        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("productId", forecast.productId());
        payload.put("days", forecast.days());
        payload.put("provider", "prophet");
        payload.put("modelVersion", "prophet");
        payload.put("forecast", forecast.forecast() == null ? List.of() : forecast.forecast().stream()
                .map(point -> {
                    final Map<String, Object> entry = new LinkedHashMap<String, Object>();
                    entry.put("date", point.ds());
                    entry.put("predictedQuantity", point.yhat());
                    entry.put("lower", point.yhatLower());
                    entry.put("upper", point.yhatUpper());
                    return entry;
                })
                .toList());
        payload.put("fallbackUsed", false);
        payload.put("warnings", List.of());
        return AgentToolResult.success("getProphetForecast", JSON.writeValueAsString(payload));
    }

    private AgentToolResult handleRecentSensorReadings(final JsonNode input) throws JsonProcessingException {
        final Long sensorId = longOrNull(input, "sensorId");
        if (sensorId == null) {
            return AgentToolResult.failure("getRecentSensorReadings", "sensorId is required");
        }
        final Integer minutes = input.has("minutes") && !input.get("minutes").isNull()
                ? input.get("minutes").asInt() : null;
        final Object readings = sensorReadingQueryService.getRecentReadings(sensorId, minutes);
        return AgentToolResult.success("getRecentSensorReadings", JSON.writeValueAsString(readings));
    }

    private AgentToolResult handleExpiringLots(final JsonNode input) throws JsonProcessingException {
        final int maxDays = input.has("days") ? input.get("days").asInt(DEFAULT_EXPIRY_DAYS) : DEFAULT_EXPIRY_DAYS;
        final List<Map<String, Object>> lots = expiryAlertRepository.findByAcknowledgedFalse().stream()
                .filter(alert -> alert.getDaysUntilExpiry() != null && alert.getDaysUntilExpiry() <= maxDays)
                .sorted(Comparator.comparing(ExpiryAlert::getDaysUntilExpiry,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(MAX_LIST_RESULTS)
                .map(alert -> {
                    final Map<String, Object> entry = new LinkedHashMap<String, Object>();
                    entry.put("lotId", alert.getLotId());
                    entry.put("productId", alert.getProductId());
                    entry.put("daysUntilExpiry", alert.getDaysUntilExpiry());
                    entry.put("alertLevel", alert.getAlertLevel());
                    entry.put("expiryDate", alert.getExpiryDate() != null ? alert.getExpiryDate().toString() : null);
                    entry.put("quantity", alert.getQuantity());
                    return entry;
                })
                .toList();
        return AgentToolResult.success("getExpiringLots", JSON.writeValueAsString(lots));
    }

    private AgentToolResult handleInventoryByLocation(final JsonNode input) throws JsonProcessingException {
        final Long locationId = longOrNull(input, "locationId");
        if (locationId == null) {
            return AgentToolResult.failure("getInventoryByLocation", "locationId is required");
        }
        final Object inventory = inventoryQueryService.getInventoryByLocation(locationId);
        return AgentToolResult.success("getInventoryByLocation", JSON.writeValueAsString(inventory));
    }

    private AgentToolResult handleRecommendationExplanationContext(final JsonNode input)
            throws JsonProcessingException {
        final Long recommendationId = longOrNull(input, "recommendationId");
        if (recommendationId == null) {
            return AgentToolResult.failure("getRecommendationExplanationContext", "recommendationId is required");
        }
        final AIRecommendationDTO recommendation = recommendationService.detailRecommendation(recommendationId);
        return AgentToolResult.success("getRecommendationExplanationContext",
                JSON.writeValueAsString(recommendation));
    }

    private AgentToolResult handleCenterInventorySummary(final JsonNode input) throws JsonProcessingException {
        final Long centerId = longOrNull(input, "centerId");
        if (centerId == null) {
            return AgentToolResult.failure("getCenterInventorySummary", "centerId is required");
        }
        final Object summary = centerInventoryAggregationService.getCenterInventorySummary(centerId);
        return AgentToolResult.success("getCenterInventorySummary", JSON.writeValueAsString(summary));
    }

    private AgentToolResult handleInventoryTransactionHistory(final JsonNode input) throws JsonProcessingException {
        final Long productId = longOrNull(input, "productId");
        final Object transactions;
        if (productId != null) {
            transactions = inventoryQueryService.getTransactionHistory(
                    productId, longOrNull(input, "locationId"), longOrNull(input, "lotId"));
        } else {
            final int limit = input.has("limit") ? input.get("limit").asInt(DEFAULT_TRANSACTION_LIMIT)
                    : DEFAULT_TRANSACTION_LIMIT;
            transactions = inventoryQueryService.getRecentTransactions(Math.min(Math.max(limit, 1), MAX_LIST_RESULTS));
        }
        return AgentToolResult.success("getInventoryTransactionHistory", JSON.writeValueAsString(transactions));
    }

    private AgentToolResult handleAbcXyzClassification(final JsonNode input) throws JsonProcessingException {
        final Object matrix = abcXyzReportService.getAbcXyzMatrix(longOrNull(input, "centerId"));
        return AgentToolResult.success("getAbcXyzClassification", JSON.writeValueAsString(matrix));
    }

    private AgentToolResult handleInventoryTurnover(final JsonNode input) throws JsonProcessingException {
        final LocalDate to = input.has("to") ? LocalDate.parse(input.get("to").asText()) : LocalDate.now();
        final LocalDate from = input.has("from") ? LocalDate.parse(input.get("from").asText()) : to.minusDays(30);
        final Object report = inventoryTurnoverReportService.generateReport(from, to, longOrNull(input, "centerId"));
        return AgentToolResult.success("getInventoryTurnover", JSON.writeValueAsString(report));
    }

    private AgentToolResult handleFillRate(final JsonNode input) throws JsonProcessingException {
        final Object report = analyticsReportingService.getFillRateReport(analyticsFilter(input));
        return AgentToolResult.success("getFillRate", JSON.writeValueAsString(report));
    }

    private AgentToolResult handleExpiryWaste(final JsonNode input) throws JsonProcessingException {
        final Object report = analyticsReportingService.getExpiryWasteReport(analyticsFilter(input));
        return AgentToolResult.success("getExpiryWaste", JSON.writeValueAsString(report));
    }

    /**
     * Triggers recommendation generation for a business date (optionally with a selected model),
     * then returns a compact summary of the generated recommendations. WRITE tool: results land in
     * {@code analytics.ai_recommendations} as proposals; a human approves them in the web UI.
     */
    private AgentToolResult handleGenerateRecommendationSnapshot(final JsonNode input)
            throws JsonProcessingException {
        final LocalDate businessDate = input.has("businessDate")
                ? LocalDate.parse(input.get("businessDate").asText())
                : LocalDate.now();
        final String model = text(input, "model", null);
        if (model != null) {
            final ForecastModel forecastModel = recommendationService.resolveForecastModel(model);
            recommendationService.generateRecommendationsForBusinessDate(businessDate, forecastModel);
        } else {
            recommendationService.generateRecommendationsForBusinessDate(businessDate);
        }

        final Long centerId = longOrNull(input, "centerId");
        final Long warehouseId = longOrNull(input, "warehouseId");
        final List<AIRecommendationDTO> generated =
                recommendationService.listRecommendations(businessDate, centerId, warehouseId, null);
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("businessDate", businessDate.toString());
        payload.put("model", model != null ? model : "default");
        payload.put("generated", true);
        payload.put("recommendationCount", generated.size());
        return AgentToolResult.success("generateRecommendationSnapshot", JSON.writeValueAsString(payload));
    }

    private AnalyticsQueryFilter analyticsFilter(final JsonNode input) {
        final LocalDate to = input.has("to") ? LocalDate.parse(input.get("to").asText()) : LocalDate.now();
        final LocalDate from = input.has("from") ? LocalDate.parse(input.get("from").asText()) : to.minusDays(30);
        return new AnalyticsQueryFilter(from, to, longOrNull(input, "centerId"), longOrNull(input, "warehouseId"));
    }

    private AgentToolResult handleCreateAISuggestionDraft(final JsonNode input) throws JsonProcessingException {
        final String type = text(input, "type", "AGENT_SUGGESTION");
        final String severity = text(input, "severity", "MEDIUM");
        final String title = text(input, "title", "Bedrock Agent 제안");
        final String summary = text(input, "summary", "");
        final String reason = text(input, "reason", "Bedrock Agent 자동 분석");
        final String recommendedAction = text(input, "recommendedAction", "");
        final String targetScopeType = text(input, "targetScopeType", "GLOBAL");
        final Long targetScopeId = longOrNull(input, "targetScopeId");

        final AISuggestionService.CreateCommand command = new AISuggestionService.CreateCommand(
                type, severity, title, summary, reason, recommendedAction,
                null, null,
                targetScopeType, targetScopeId,
                null, null,
                "BEDROCK_AGENT", "AI_AGENT",
                null, null, null, null, null, null, null,
                "MANUAL_APPROVAL_REQUIRED",
                null, null, null, null);

        final AISuggestion suggestion = aiSuggestionService.create(command, null, UUID.randomUUID().toString());
        final var responsePayload = new LinkedHashMap<String, Object>();
        responsePayload.put("suggestionId", suggestion.getId());
        responsePayload.put("status", suggestion.getStatus());
        responsePayload.put("title", suggestion.getTitle());
        return AgentToolResult.success("createAISuggestionDraft", JSON.writeValueAsString(responsePayload));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Long longOrNull(final JsonNode node, final String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asLong();
        }
        return null;
    }

    private static String text(final JsonNode node, final String field, final String defaultValue) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText();
        }
        return defaultValue;
    }
}
