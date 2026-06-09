package com.stockops.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.stockops.ai.forecast.ForecastContext;
import com.stockops.ai.forecast.ForecastContext.DemandDataPoint;
import com.stockops.ai.forecast.ForecastContext.ForecastParameters;
import com.stockops.ai.forecast.ForecastContext.LeadTimeInfo;
import com.stockops.ai.forecast.ForecastModel;
import com.stockops.ai.forecast.ForecastResult;
import com.stockops.dto.AIRecommendationDTO;
import com.stockops.entity.Product;
import com.stockops.entity.PurchaseOrder;
import com.stockops.entity.User;
import com.stockops.entity.ai.AIForecastSnapshot;
import com.stockops.entity.ai.AIRecommendation;
import com.stockops.entity.ai.AIRecommendationStatus;
import com.stockops.exception.InvalidOperationException;
import com.stockops.exception.ResourceNotFoundException;
import com.stockops.repository.ProductRepository;
import com.stockops.repository.ai.AIForecastSnapshotRepository;
import com.stockops.repository.ai.AIRecommendationRepository;
import com.stockops.security.ScopeGuard;
import com.stockops.service.PurchaseOrderService;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds and serves deterministic AI reorder recommendations from the analytics read model.
 * Forecasts remain fully explainable because every recommendation persists its snapshot inputs.
 * <p>
 * Forecast computation is delegated to a pluggable {@link ForecastModel} seam,
 * defaulting to {@link com.stockops.ai.forecast.StatisticalForecastModel}.
 *
 * @author StockOps Team
 * @since 2.0
 */
@Service
public class AIRecommendationService {

    private static final int FORECAST_HORIZON_DAYS = 7;

    private final AIRecommendationProperties properties;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ProductRepository productRepository;
    private final AIForecastSnapshotRepository forecastSnapshotRepository;
    private final AIRecommendationRepository recommendationRepository;
    private final PurchaseOrderService purchaseOrderService;
    private final ScopeGuard scopeGuard;
    private final ForecastModel defaultForecastModel;
    private final Map<String, ForecastModel> forecastModels;

    public AIRecommendationService(
            final AIRecommendationProperties properties,
            final NamedParameterJdbcTemplate jdbcTemplate,
            final ProductRepository productRepository,
            final AIForecastSnapshotRepository forecastSnapshotRepository,
            final AIRecommendationRepository recommendationRepository,
            final PurchaseOrderService purchaseOrderService,
            final ScopeGuard scopeGuard,
            @Qualifier("statisticalForecastModel") final ForecastModel defaultForecastModel,
            final Map<String, ForecastModel> forecastModels) {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.productRepository = productRepository;
        this.forecastSnapshotRepository = forecastSnapshotRepository;
        this.recommendationRepository = recommendationRepository;
        this.purchaseOrderService = purchaseOrderService;
        this.scopeGuard = scopeGuard;
        this.defaultForecastModel = defaultForecastModel;
        this.forecastModels = forecastModels;
    }

    /**
     * Resolves a ForecastModel by identifier, falling back to the default statistical model.
     *
     * @param modelId optional model identifier (e.g. "statistical", "external")
     * @return the resolved ForecastModel implementation
     */
    public ForecastModel resolveForecastModel(final String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return defaultForecastModel;
        }
        return forecastModels.values().stream()
                .filter(model -> model.getModelId().equals(modelId))
                .findFirst()
                .orElse(defaultForecastModel);
    }

    /**
     * Generates or refreshes AI recommendation snapshots for the supplied business date.
     * Previously approved recommendations are preserved so approval history remains stable.
     * Evicts the recommendations cache so subsequent reads reflect fresh data.
     *
     * @param businessDate business date to generate
     */
    @Transactional
    @CacheEvict(value = "ai::recommendations", allEntries = true)
    public void generateRecommendationsForBusinessDate(final LocalDate businessDate) {
        generateRecommendationsForBusinessDate(businessDate, defaultForecastModel);
    }

    /**
     * Generates or refreshes AI recommendation snapshots using the specified forecast model.
     * Evicts the recommendations cache so subsequent reads reflect fresh data.
     *
     * @param businessDate business date to generate
     * @param forecastModel the forecast model to use for computation
     */
    @Transactional
    @CacheEvict(value = "ai::recommendations", allEntries = true)
    public void generateRecommendationsForBusinessDate(final LocalDate businessDate, final ForecastModel forecastModel) {
        final Map<DimensionKey, ProductDimensionContext> contexts = loadDimensionContexts(businessDate);
        final Map<DimensionKey, AIForecastSnapshot> existingForecasts = indexForecasts(forecastSnapshotRepository.findByBusinessDate(businessDate));
        final Map<DimensionKey, AIRecommendation> existingRecommendations = indexRecommendations(
                recommendationRepository.findByBusinessDate(businessDate));
        final Set<DimensionKey> processedKeys = new HashSet<>();

        for (Map.Entry<DimensionKey, ProductDimensionContext> entry : contexts.entrySet()) {
            final DimensionKey key = entry.getKey();
            final ProductDimensionContext context = entry.getValue();
            final AIRecommendation existingRecommendation = existingRecommendations.get(key);

            if (isApproved(existingRecommendation)) {
                processedKeys.add(key);
                continue;
            }

            final ForecastContext forecastContext = buildForecastContext(context, businessDate);
            final ForecastResult computation = forecastModel.computeForecast(forecastContext);

            final AIForecastSnapshot forecastSnapshot = upsertForecastSnapshot(
                    existingForecasts.get(key),
                    key,
                    businessDate,
                    computation);
            forecastSnapshotRepository.save(forecastSnapshot);

            final AIRecommendation recommendation = upsertRecommendation(
                    existingRecommendation,
                    forecastSnapshot,
                    key,
                    context,
                    computation,
                    businessDate);
            recommendationRepository.save(recommendation);
            processedKeys.add(key);
        }

        deleteStaleUnapprovedSnapshots(existingRecommendations, existingForecasts, processedKeys);
        log.info("Generated {} AI recommendation snapshots for {} using model {}",
                processedKeys.size(), businessDate, forecastModel.getModelId());
    }

    /**
     * Lists scoped recommendation snapshots for the requested filters.
     * Cached for 5 minutes; evicted when recommendations are regenerated.
     *
     * @param businessDate optional business date, defaults to today in the business timezone
     * @param centerId optional center filter
     * @param warehouseId optional warehouse filter
     * @param productId optional product filter
     * @return filtered recommendation payloads
     */
    @Cacheable(value = "ai::recommendations", key = "(#businessDate ?: T(java.time.LocalDate).now(T(java.time.ZoneId).of('Asia/Seoul'))) + '-' + (#centerId ?: 'all') + '-' + (#warehouseId ?: 'all') + '-' + (#productId ?: 'all')")
    @Transactional(readOnly = true)
    public List<AIRecommendationDTO> listRecommendations(final LocalDate businessDate,
                                                         final Long centerId,
                                                         final Long warehouseId,
                                                         final Long productId) {
        final LocalDate effectiveBusinessDate = businessDate == null ? LocalDate.now(getBusinessZone()) : businessDate;
        if (centerId != null && !scopeGuard.filterCenterIds(List.of(centerId)).contains(centerId)) {
            return List.of();
        }
        if (warehouseId != null && !scopeGuard.filterWarehouseIds(List.of(warehouseId)).contains(warehouseId)) {
            return List.of();
        }

        List<AIRecommendation> recommendations = recommendationRepository
                .findByBusinessDateOrderByRecommendedQuantityDescIdAsc(effectiveBusinessDate);
        recommendations = scopeGuard.filterByCenterWarehouseScope(
                recommendations,
                AIRecommendation::getCenterId,
                AIRecommendation::getWarehouseId);

        final List<AIRecommendation> filteredRecommendations = recommendations.stream()
                .filter(recommendation -> centerId == null || centerId.equals(recommendation.getCenterId()))
                .filter(recommendation -> warehouseId == null || warehouseId.equals(recommendation.getWarehouseId()))
                .filter(recommendation -> productId == null || productId.equals(recommendation.getProductId()))
                .toList();

        return toDTOs(filteredRecommendations);
    }

    /**
     * Returns a single recommendation by id, scoped to the current user's center/warehouse access.
     *
     * @param recommendationId recommendation identifier
     * @return recommendation payload
     * @throws ResourceNotFoundException when no recommendation exists for the given id
     */
    @Transactional(readOnly = true)
    public AIRecommendationDTO detailRecommendation(final Long recommendationId) {
        final AIRecommendation recommendation = recommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new ResourceNotFoundException("AI recommendation not found: " + recommendationId));
        scopeGuard.assertCenterWarehouseAccess(recommendation.getCenterId(), recommendation.getWarehouseId());
        return toDTO(recommendation, loadProducts(Set.of(recommendation.getProductId())));
    }

    /**
     * Approves one ready recommendation into a draft purchase order.
     * The method never submits or auto-accepts the draft downstream.
     * Evicts the recommendations cache so the approved status is reflected immediately.
     *
     * @param recommendationId recommendation identifier
     * @param currentUser approving user
     * @return approved recommendation payload including the linked draft purchase order
     */
    @Transactional
    @CacheEvict(value = "ai::recommendations", allEntries = true)
    public AIRecommendationDTO approveRecommendation(final Long recommendationId, final User currentUser) {
        final AIRecommendation recommendation = recommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new ResourceNotFoundException("AI recommendation not found: " + recommendationId));
        scopeGuard.assertCenterWarehouseAccess(recommendation.getCenterId(), recommendation.getWarehouseId());

        if (recommendation.getStatus() != AIRecommendationStatus.READY_FOR_APPROVAL) {
            throw new InvalidOperationException("Only READY_FOR_APPROVAL recommendations can be approved");
        }
        if (recommendation.getRecommendedQuantity() == null || recommendation.getRecommendedQuantity() <= 0) {
            throw new InvalidOperationException("Recommendation quantity must be positive to create a draft purchase order");
        }

        PurchaseOrder purchaseOrder = purchaseOrderService.create(
                recommendation.getCenterId(),
                recommendation.getWarehouseId(),
                currentUser);
        purchaseOrder = purchaseOrderService.addItem(
                purchaseOrder.getId(),
                recommendation.getProductId(),
                recommendation.getRecommendedQuantity());

        recommendation.setApprovedPurchaseOrder(purchaseOrder);
        recommendation.setApprovedBy(currentUser);
        recommendation.setApprovedAt(Instant.now());
        recommendation.setStatus(AIRecommendationStatus.APPROVED_TO_DRAFT);
        recommendation.setExplanationSummary(appendApprovalExplanation(recommendation.getExplanationSummary(), purchaseOrder));

        final AIRecommendation savedRecommendation = recommendationRepository.save(recommendation);
        return toDTO(savedRecommendation, loadProducts(Set.of(savedRecommendation.getProductId())));
    }

    private ForecastContext buildForecastContext(final ProductDimensionContext context, final LocalDate businessDate) {
        final List<DemandDataPoint> demandDataPoints = context.demandRows().stream()
                .map(row -> new DemandDataPoint(
                        row.businessDate(),
                        row.confirmedOutboundQuantity(),
                        row.confirmedOutboundEventCount()))
                .toList();

        final LeadTimeInfo leadTimeInfo = new LeadTimeInfo(
                context.leadTimeStats().totalLeadTimeHours(),
                context.leadTimeStats().sampleCount(),
                properties.getDefaultLeadTimeDays());

        final ForecastParameters parameters = new ForecastParameters(
                properties.getTrailingAverageDays(),
                properties.getSameWeekdayLookbackWeeks(),
                FORECAST_HORIZON_DAYS,
                properties.getForecastHistoryDays(),
                new BigDecimal("0.70"),
                new BigDecimal("0.30"));

        return new ForecastContext(
                context.key().productId(),
                context.key().centerId(),
                context.key().warehouseId(),
                businessDate,
                context.currentStockQuantity(),
                context.safetyStockQuantity(),
                demandDataPoints,
                leadTimeInfo,
                parameters);
    }

    private Map<Long, Product> loadProducts(final Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        final Map<Long, Product> products = new HashMap<>();
        for (Product product : productRepository.findAllById(productIds)) {
            products.put(product.getId(), product);
        }
        return products;
    }

    private List<AIRecommendationDTO> toDTOs(final List<AIRecommendation> recommendations) {
        final Set<Long> productIds = recommendations.stream().map(AIRecommendation::getProductId).collect(java.util.stream.Collectors.toSet());
        final Map<Long, Product> products = loadProducts(productIds);
        return recommendations.stream().map(recommendation -> toDTO(recommendation, products)).toList();
    }

    private AIRecommendationDTO toDTO(final AIRecommendation recommendation, final Map<Long, Product> products) {
        final Product product = products.get(recommendation.getProductId());
        final AIForecastSnapshot forecastSnapshot = recommendation.getForecastSnapshot();
        return new AIRecommendationDTO(
                recommendation.getId(),
                recommendation.getBusinessDate(),
                recommendation.getProductId(),
                product == null ? null : product.getName(),
                product == null ? null : product.getBarcode(),
                recommendation.getCenterId(),
                recommendation.getWarehouseId(),
                recommendation.getStatus(),
                recommendation.getCurrentStockQuantity(),
                recommendation.getSafetyStockQuantity(),
                recommendation.getRecommendedQuantity(),
                forecastSnapshot.getSevenDayForecastQuantity(),
                forecastSnapshot.getLeadTimeDays(),
                forecastSnapshot.getLeadTimeDemandQuantity(),
                forecastSnapshot.getTrailingSevenDayAverage(),
                forecastSnapshot.getSameWeekdayAverage(),
                forecastSnapshot.getWeightedDailyDemand(),
                forecastSnapshot.getDemandEventCount(),
                forecastSnapshot.isInsufficientHistory(),
                recommendation.getExplanationSummary(),
                recommendation.getApprovedPurchaseOrder() == null ? null : recommendation.getApprovedPurchaseOrder().getId(),
                recommendation.getApprovedPurchaseOrder() == null ? null : recommendation.getApprovedPurchaseOrder().getPoNumber(),
                recommendation.getApprovedAt(),
                recommendation.getApprovedBy() == null ? null : recommendation.getApprovedBy().getId(),
                forecastSnapshot.getModelVersion(),
                recommendation.getCreatedAt(),
                recommendation.getUpdatedAt());
    }

    private Map<DimensionKey, ProductDimensionContext> loadDimensionContexts(final LocalDate businessDate) {
        final LocalDate demandHistoryFrom = businessDate.minusDays(properties.getForecastHistoryDays());
        final LocalDate demandHistoryTo = businessDate.minusDays(1);

        final List<DemandHistoryRow> demandRows = loadDemandHistoryRows(demandHistoryFrom, demandHistoryTo);
        final Map<DimensionKey, Integer> currentStockByDimension = loadCurrentStockByDimension(businessDate);
        final Map<DimensionKey, LeadTimeStats> leadTimeByDimension = loadLeadTimeByDimension(
                businessDate.minusDays(properties.getLeadTimeLookbackDays()),
                businessDate.minusDays(1));

        final Set<DimensionKey> dimensionKeys = new HashSet<>();
        dimensionKeys.addAll(currentStockByDimension.keySet());
        dimensionKeys.addAll(leadTimeByDimension.keySet());
        for (DemandHistoryRow demandRow : demandRows) {
            dimensionKeys.add(demandRow.dimensionKey());
        }

        final Map<Long, Product> products = loadProducts(dimensionKeys.stream().map(DimensionKey::productId).collect(java.util.stream.Collectors.toSet()));
        final Map<DimensionKey, List<DemandHistoryRow>> demandRowsByDimension = new HashMap<>();
        for (DemandHistoryRow demandRow : demandRows) {
            demandRowsByDimension.computeIfAbsent(demandRow.dimensionKey(), ignored -> new ArrayList<>()).add(demandRow);
        }

        final Map<DimensionKey, ProductDimensionContext> contexts = new LinkedHashMap<>();
        for (DimensionKey key : dimensionKeys.stream().sorted(DimensionKey::compareTo).toList()) {
            final Product product = products.get(key.productId());
            if (product == null) {
                continue;
            }

            contexts.put(key, new ProductDimensionContext(
                    key,
                    product,
                    currentStockByDimension.getOrDefault(key, 0),
                    leadTimeByDimension.getOrDefault(key, LeadTimeStats.defaultFor(properties.getDefaultLeadTimeDays())),
                    demandRowsByDimension.getOrDefault(key, List.of())));
        }
        return contexts;
    }

    private List<DemandHistoryRow> loadDemandHistoryRows(final LocalDate from, final LocalDate to) {
        if (to.isBefore(from)) {
            return List.of();
        }
        final String sql = """
                SELECT business_date,
                       product_id,
                       center_id,
                       warehouse_id,
                       confirmed_outbound_quantity,
                       confirmed_outbound_event_count
                FROM analytics.daily_demand_history
                WHERE business_date BETWEEN :fromDate AND :toDate
                """;

        final MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("fromDate", from)
                .addValue("toDate", to);
        return jdbcTemplate.query(sql, parameters, (rs, rowNum) -> mapDemandHistoryRow(rs));
    }

    private Map<DimensionKey, Integer> loadCurrentStockByDimension(final LocalDate businessDate) {
        final String sql = """
                SELECT sp.product_id,
                       sp.center_id,
                       sp.warehouse_id,
                       sp.available_quantity
                FROM analytics.daily_stock_position sp
                JOIN (
                    SELECT product_id,
                           center_id,
                           warehouse_id,
                           MAX(business_date) AS max_business_date
                    FROM analytics.daily_stock_position
                    WHERE business_date <= :businessDate
                    GROUP BY product_id, center_id, warehouse_id
                ) latest
                    ON latest.product_id = sp.product_id
                   AND latest.center_id = sp.center_id
                   AND latest.warehouse_id = sp.warehouse_id
                   AND latest.max_business_date = sp.business_date
                """;

        final Map<DimensionKey, Integer> currentStockByDimension = new HashMap<>();
        jdbcTemplate.query(sql, new MapSqlParameterSource("businessDate", businessDate), rs -> {
            final DimensionKey key = new DimensionKey(
                    rs.getLong("product_id"),
                    rs.getLong("center_id"),
                    rs.getLong("warehouse_id"));
            currentStockByDimension.put(key, rs.getInt("available_quantity"));
        });
        return currentStockByDimension;
    }

    private Map<DimensionKey, LeadTimeStats> loadLeadTimeByDimension(final LocalDate from, final LocalDate to) {
        if (to.isBefore(from)) {
            return Map.of();
        }
        final String sql = """
                SELECT product_id,
                       center_id,
                       warehouse_id,
                       SUM(total_lead_time_hours) AS total_lead_time_hours,
                       SUM(lead_time_sample_count) AS total_samples
                FROM analytics.daily_purchase_order_lead_time
                WHERE business_date BETWEEN :fromDate AND :toDate
                  AND lead_time_sample_count > 0
                GROUP BY product_id, center_id, warehouse_id
                """;

        final Map<DimensionKey, LeadTimeStats> leadTimeByDimension = new HashMap<>();
        final MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("fromDate", from)
                .addValue("toDate", to);
        jdbcTemplate.query(sql, parameters, rs -> {
            final DimensionKey key = new DimensionKey(
                    rs.getLong("product_id"),
                    rs.getLong("center_id"),
                    rs.getLong("warehouse_id"));
            final long totalLeadTimeHours = rs.getLong("total_lead_time_hours");
            final int samples = rs.getInt("total_samples");
            leadTimeByDimension.put(key, new LeadTimeStats(totalLeadTimeHours, samples));
        });
        return leadTimeByDimension;
    }

    private AIForecastSnapshot upsertForecastSnapshot(final AIForecastSnapshot existingSnapshot,
                                                     final DimensionKey key,
                                                     final LocalDate businessDate,
                                                     final ForecastResult computation) {
        final AIForecastSnapshot snapshot = existingSnapshot == null ? new AIForecastSnapshot() : existingSnapshot;
        snapshot.setBusinessDate(businessDate);
        snapshot.setForecastStartDate(businessDate);
        snapshot.setForecastEndDate(businessDate.plusDays(FORECAST_HORIZON_DAYS - 1L));
        snapshot.setProductId(key.productId());
        snapshot.setCenterId(key.centerId());
        snapshot.setWarehouseId(key.warehouseId());
        snapshot.setTrailingSevenDayAverage(computation.trailingAverage());
        snapshot.setSameWeekdayAverage(computation.sameWeekdayAverage());
        snapshot.setWeightedDailyDemand(computation.weightedDailyDemand());
        snapshot.setSevenDayForecastQuantity(computation.sevenDayForecastQuantity());
        snapshot.setLeadTimeDays(computation.leadTimeDays());
        snapshot.setLeadTimeDemandQuantity(computation.leadTimeDemandQuantity());
        snapshot.setHistoryDaysConsidered(computation.historyDaysConsidered());
        snapshot.setDemandEventCount(computation.demandEventCount());
        snapshot.setInsufficientHistory(computation.insufficientHistory());
        snapshot.setExplanationSummary(computation.explanationSummary());
        snapshot.setModelVersion(computation.modelVersion());
        return snapshot;
    }

    private AIRecommendation upsertRecommendation(final AIRecommendation existingRecommendation,
                                                  final AIForecastSnapshot forecastSnapshot,
                                                  final DimensionKey key,
                                                  final ProductDimensionContext context,
                                                  final ForecastResult computation,
                                                  final LocalDate businessDate) {
        final AIRecommendation recommendation = existingRecommendation == null ? new AIRecommendation() : existingRecommendation;
        recommendation.setBusinessDate(businessDate);
        recommendation.setProductId(key.productId());
        recommendation.setCenterId(key.centerId());
        recommendation.setWarehouseId(key.warehouseId());
        recommendation.setForecastSnapshot(forecastSnapshot);
        recommendation.setCurrentStockQuantity(context.currentStockQuantity());
        recommendation.setSafetyStockQuantity(context.safetyStockQuantity());
        recommendation.setRecommendedQuantity(computation.recommendedQuantity());
        recommendation.setStatus(computation.status());
        recommendation.setExplanationSummary(computation.explanationSummary());
        recommendation.setApprovedAt(null);
        recommendation.setApprovedBy(null);
        recommendation.setApprovedPurchaseOrder(null);
        return recommendation;
    }

    private void deleteStaleUnapprovedSnapshots(final Map<DimensionKey, AIRecommendation> existingRecommendations,
                                                final Map<DimensionKey, AIForecastSnapshot> existingForecasts,
                                                final Set<DimensionKey> processedKeys) {
        final List<AIRecommendation> recommendationsToDelete = existingRecommendations.entrySet().stream()
                .filter(entry -> !processedKeys.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(recommendation -> !isApproved(recommendation))
                .toList();
        if (!recommendationsToDelete.isEmpty()) {
            recommendationRepository.deleteAll(recommendationsToDelete);
        }

        final List<AIForecastSnapshot> forecastsToDelete = existingForecasts.entrySet().stream()
                .filter(entry -> !processedKeys.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        if (!forecastsToDelete.isEmpty()) {
            forecastSnapshotRepository.deleteAll(forecastsToDelete);
        }
    }

    private boolean isApproved(final AIRecommendation recommendation) {
        return recommendation != null
                && recommendation.getStatus() == AIRecommendationStatus.APPROVED_TO_DRAFT
                && recommendation.getApprovedPurchaseOrder() != null;
    }

    private String appendApprovalExplanation(final String existingExplanation, final PurchaseOrder purchaseOrder) {
        return (existingExplanation == null ? "" : existingExplanation + " ")
                + "Approved into draft purchase order " + purchaseOrder.getPoNumber();
    }

    private ZoneId getBusinessZone() {
        return ZoneId.of(properties.getBusinessZone());
    }

    private Map<DimensionKey, AIForecastSnapshot> indexForecasts(final List<AIForecastSnapshot> snapshots) {
        final Map<DimensionKey, AIForecastSnapshot> indexed = new HashMap<>();
        for (AIForecastSnapshot snapshot : snapshots) {
            indexed.put(new DimensionKey(snapshot.getProductId(), snapshot.getCenterId(), snapshot.getWarehouseId()), snapshot);
        }
        return indexed;
    }

    private Map<DimensionKey, AIRecommendation> indexRecommendations(final List<AIRecommendation> recommendations) {
        final Map<DimensionKey, AIRecommendation> indexed = new HashMap<>();
        for (AIRecommendation recommendation : recommendations) {
            indexed.put(new DimensionKey(recommendation.getProductId(), recommendation.getCenterId(), recommendation.getWarehouseId()), recommendation);
        }
        return indexed;
    }

    private DemandHistoryRow mapDemandHistoryRow(final ResultSet resultSet) throws SQLException {
        return new DemandHistoryRow(
                resultSet.getObject("business_date", LocalDate.class),
                new DimensionKey(resultSet.getLong("product_id"), resultSet.getLong("center_id"), resultSet.getLong("warehouse_id")),
                resultSet.getInt("confirmed_outbound_quantity"),
                resultSet.getInt("confirmed_outbound_event_count"));
    }

    private record ProductDimensionContext(
            DimensionKey key,
            Product product,
            int currentStockQuantity,
            LeadTimeStats leadTimeStats,
            List<DemandHistoryRow> demandRows) {

        private int safetyStockQuantity() {
            return product == null || product.getSafetyStockQuantity() == null ? 0 : product.getSafetyStockQuantity();
        }
    }

    private record DemandHistoryRow(
            LocalDate businessDate,
            DimensionKey dimensionKey,
            int confirmedOutboundQuantity,
            int confirmedOutboundEventCount) {
    }

    private record LeadTimeStats(long totalLeadTimeHours, int sampleCount) {

        private static LeadTimeStats defaultFor(final int defaultLeadTimeDays) {
            return new LeadTimeStats((long) defaultLeadTimeDays * 24L, 1);
        }

        private int resolvedLeadTimeDays(final int defaultLeadTimeDays) {
            if (sampleCount <= 0) {
                return Math.max(defaultLeadTimeDays, 1);
            }
            final BigDecimal averageHours = BigDecimal.valueOf(totalLeadTimeHours)
                    .divide(BigDecimal.valueOf(sampleCount), 2, java.math.RoundingMode.HALF_UP);
            return Math.max(averageHours.divide(BigDecimal.valueOf(24), 0, java.math.RoundingMode.CEILING).intValue(), 1);
        }
    }

    private record DimensionKey(Long productId, Long centerId, Long warehouseId) implements Comparable<DimensionKey> {

        @Override
        public int compareTo(final DimensionKey other) {
            int productCompare = productId.compareTo(other.productId);
            if (productCompare != 0) {
                return productCompare;
            }
            int centerCompare = centerId.compareTo(other.centerId);
            if (centerCompare != 0) {
                return centerCompare;
            }
            return warehouseId.compareTo(other.warehouseId);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(AIRecommendationService.class);
}
