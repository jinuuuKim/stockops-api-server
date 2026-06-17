package com.stockops.ai.bedrock.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockops.ai.forecast.AiForecastClient;
import com.stockops.dto.AIRecommendationDTO;
import com.stockops.dto.InventoryDTO;
import com.stockops.dto.RecentSensorReadingsResponse;
import com.stockops.entity.ExpiryAlert;
import com.stockops.entity.PurchaseOrderShipment;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentToolDispatcherTest {

    @Mock
    private InventoryQueryService inventoryQueryService;
    @Mock
    private AIRecommendationService recommendationService;
    @Mock
    private EnvironmentQueryService environmentQueryService;
    @Mock
    private AISuggestionService aiSuggestionService;
    @Mock
    private PurchaseOrderShipmentRepository shipmentRepository;
    @Mock
    private AiForecastClient aiForecastClient;
    @Mock
    private SensorReadingQueryService sensorReadingQueryService;
    @Mock
    private ExpiryAlertRepository expiryAlertRepository;
    @Mock
    private CenterInventoryAggregationService centerInventoryAggregationService;
    @Mock
    private AbcXyzReportService abcXyzReportService;
    @Mock
    private InventoryTurnoverReportService inventoryTurnoverReportService;
    @Mock
    private AnalyticsReportingService analyticsReportingService;

    private AgentToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new AgentToolDispatcher(
                inventoryQueryService,
                recommendationService,
                environmentQueryService,
                aiSuggestionService,
                shipmentRepository,
                aiForecastClient,
                sensorReadingQueryService,
                expiryAlertRepository,
                centerInventoryAggregationService,
                abcXyzReportService,
                inventoryTurnoverReportService,
                analyticsReportingService);
    }

    @Test
    void dispatch_searchInventory_delegatesToService() {
        when(inventoryQueryService.searchInventory("야채교자"))
                .thenReturn(Map.of("query", "야채교자", "productMatchCount", 1));

        final AgentToolResult result = dispatcher.dispatch("searchInventory", "{\"query\": \"야채교자\"}");

        assertThat(result.success()).isTrue();
        assertThat(result.toolName()).isEqualTo("searchInventory");
        assertThat(result.resultJson()).contains("\"productMatchCount\":1");
    }

    @Test
    void dispatch_searchInventory_withoutQuery_returnsFailure() {
        final AgentToolResult result = dispatcher.dispatch("searchInventory", "{}");

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("query is required");
    }

    @Test
    void dispatch_getProphetForecast_mapsForecastPoints() {
        when(aiForecastClient.getForecast(1L, 7)).thenReturn(new AiForecastClient.AiForecastResponse(
                1L, 7, List.of(new AiForecastClient.AiForecastResponse.ForecastPoint(
                        "2026-06-12", 12.4, 8.1, 16.8))));

        final AgentToolResult result = dispatcher.dispatch("getProphetForecast", "{\"productId\": 1, \"days\": 7}");

        assertThat(result.success()).isTrue();
        assertThat(result.toolName()).isEqualTo("getProphetForecast");
        assertThat(result.resultJson()).contains("\"provider\":\"prophet\"");
        assertThat(result.resultJson()).contains("\"date\":\"2026-06-12\"");
        assertThat(result.resultJson()).contains("\"predictedQuantity\":12.4");
        assertThat(result.resultJson()).contains("\"lower\":8.1");
        assertThat(result.resultJson()).contains("\"upper\":16.8");
        assertThat(result.resultJson()).contains("\"fallbackUsed\":false");
    }

    @Test
    void dispatch_getProphetForecast_defaultsAndCapsDays() {
        when(aiForecastClient.getForecast(1L, 7)).thenReturn(new AiForecastClient.AiForecastResponse(1L, 7, List.of()));

        dispatcher.dispatch("getProphetForecast", "{\"productId\": 1}");
        verify(aiForecastClient).getForecast(1L, 7);

        when(aiForecastClient.getForecast(1L, 30)).thenReturn(new AiForecastClient.AiForecastResponse(1L, 30, List.of()));
        dispatcher.dispatch("getProphetForecast", "{\"productId\": 1, \"days\": 90}");
        verify(aiForecastClient).getForecast(1L, 30);
    }

    @Test
    void dispatch_getProphetForecast_withoutProductId_returnsFailure() {
        final AgentToolResult result = dispatcher.dispatch("getProphetForecast", "{}");

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("productId");
    }

    @Test
    void dispatch_getProphetForecast_serviceUnavailable_returnsFailure() {
        when(aiForecastClient.getForecast(1L, 7)).thenReturn(null);

        final AgentToolResult result = dispatcher.dispatch("getProphetForecast", "{\"productId\": 1}");

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Prophet");
    }

    @Test
    void dispatch_getInventoryRisk_withoutProductId_callsGetAllInventory() {
        when(inventoryQueryService.getAllInventory()).thenReturn(List.of());

        final AgentToolResult result = dispatcher.dispatch("getInventoryRisk", "{}");

        assertThat(result.success()).isTrue();
        assertThat(result.toolName()).isEqualTo("getInventoryRisk");
        assertThat(result.resultJson()).isNotNull();
        verify(inventoryQueryService).getAllInventory();
    }

    @Test
    void dispatch_getInventoryRisk_withProductId_callsGetInventoryByProduct() {
        when(inventoryQueryService.getInventoryByProduct(42L)).thenReturn(List.of());

        final AgentToolResult result = dispatcher.dispatch("getInventoryRisk", "{\"productId\": 42}");

        assertThat(result.success()).isTrue();
        verify(inventoryQueryService).getInventoryByProduct(42L);
    }

    @Test
    void dispatch_getForecastRecommendation_callsListRecommendations() {
        when(recommendationService.listRecommendations(any(), isNull(), isNull(), isNull()))
                .thenReturn(List.of());

        final AgentToolResult result = dispatcher.dispatch("getForecastRecommendation", "{}");

        assertThat(result.success()).isTrue();
        verify(recommendationService).listRecommendations(any(), isNull(), isNull(), isNull());
    }

    @Test
    void dispatch_getSensorAnomalies_callsGetAlerts() {
        when(environmentQueryService.getAlerts(7)).thenReturn(List.of());

        final AgentToolResult result = dispatcher.dispatch("getSensorAnomalies", "{\"days\": 7}");

        assertThat(result.success()).isTrue();
        verify(environmentQueryService).getAlerts(7);
    }

    @Test
    void dispatch_getPurchaseOrderDelaySummary_emptyList_returnsEmptyJson() {
        when(shipmentRepository.findByEtaDateBeforeAndDeliveredAtIsNull(
                org.mockito.ArgumentMatchers.any(LocalDate.class))).thenReturn(List.of());

        final AgentToolResult result = dispatcher.dispatch("getPurchaseOrderDelaySummary", "{}");

        assertThat(result.success()).isTrue();
        assertThat(result.resultJson()).isEqualTo("[]");
    }

    @Test
    void dispatch_getPurchaseOrderDelaySummary_withOverdueShipment_includesDaysOverdue() {
        final PurchaseOrderShipment shipment = new PurchaseOrderShipment();
        shipment.setShipmentNumber("SHP-001");
        shipment.setCarrier("FedEx");
        shipment.setEtaDate(LocalDate.now().minusDays(3));

        when(shipmentRepository.findByEtaDateBeforeAndDeliveredAtIsNull(
                org.mockito.ArgumentMatchers.any(LocalDate.class))).thenReturn(List.of(shipment));

        final AgentToolResult result = dispatcher.dispatch("getPurchaseOrderDelaySummary", "{}");

        assertThat(result.success()).isTrue();
        assertThat(result.resultJson()).contains("\"daysOverdue\":3");
        assertThat(result.resultJson()).contains("SHP-001");
    }

    @Test
    void dispatch_getRecentSensorReadings_requiresSensorId() {
        final AgentToolResult result = dispatcher.dispatch("getRecentSensorReadings", "{}");

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("sensorId");
    }

    @Test
    void dispatch_getRecentSensorReadings_callsQueryService() {
        when(sensorReadingQueryService.getRecentReadings(12L, 10))
                .thenReturn(new RecentSensorReadingsResponse(12L, 10, List.of()));

        final AgentToolResult result =
                dispatcher.dispatch("getRecentSensorReadings", "{\"sensorId\": 12, \"minutes\": 10}");

        assertThat(result.success()).isTrue();
        verify(sensorReadingQueryService).getRecentReadings(12L, 10);
    }

    @Test
    void dispatch_getExpiringLots_filtersByDaysAndMaps() {
        when(expiryAlertRepository.findByAcknowledgedFalse()).thenReturn(List.of(
                expiryAlert(100L, 7, "WARNING"),
                expiryAlert(200L, 60, "INFO")));

        final AgentToolResult result = dispatcher.dispatch("getExpiringLots", "{\"days\": 30}");

        assertThat(result.success()).isTrue();
        assertThat(result.resultJson()).contains("\"lotId\":100").contains("\"daysUntilExpiry\":7");
        assertThat(result.resultJson()).doesNotContain("\"lotId\":200");
    }

    @Test
    void dispatch_getInventoryByLocation_requiresLocationId() {
        final AgentToolResult result = dispatcher.dispatch("getInventoryByLocation", "{}");

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("locationId");
    }

    @Test
    void dispatch_getCenterInventorySummary_callsService() {
        when(centerInventoryAggregationService.getCenterInventorySummary(3L))
                .thenReturn(Map.of("centerId", 3L, "totalQuantity", 500));

        final AgentToolResult result = dispatcher.dispatch("getCenterInventorySummary", "{\"centerId\": 3}");

        assertThat(result.success()).isTrue();
        verify(centerInventoryAggregationService).getCenterInventorySummary(3L);
    }

    @Test
    void dispatch_getInventoryTurnover_defaultsDateWindow() {
        when(inventoryTurnoverReportService.generateReport(any(), any(), isNull())).thenReturn(List.of());

        final AgentToolResult result = dispatcher.dispatch("getInventoryTurnover", "{}");

        assertThat(result.success()).isTrue();
        verify(inventoryTurnoverReportService).generateReport(any(), any(), isNull());
    }

    @Test
    void dispatch_generateRecommendationSnapshot_generatesAndSummarizes() {
        when(recommendationService.listRecommendations(any(), isNull(), isNull(), isNull()))
                .thenReturn(List.of());

        final AgentToolResult result = dispatcher.dispatch(
                "generateRecommendationSnapshot", "{\"businessDate\": \"2026-06-12\"}");

        assertThat(result.success()).isTrue();
        assertThat(result.resultJson()).contains("\"generated\":true");
        assertThat(result.resultJson()).contains("\"recommendationCount\":0");
        verify(recommendationService).generateRecommendationsForBusinessDate(LocalDate.parse("2026-06-12"));
    }

    @Test
    void dispatch_unknownTool_returnsFailure() {
        final AgentToolResult result = dispatcher.dispatch("nonExistentTool", "{}");

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Unknown tool");
    }

    @Test
    void dispatch_nullInput_doesNotThrow() {
        when(inventoryQueryService.getAllInventory()).thenReturn(List.of());

        final AgentToolResult result = dispatcher.dispatch("getInventoryRisk", null);

        assertThat(result.success()).isTrue();
    }

    @Test
    void dispatch_malformedJson_returnsFailure() {
        final AgentToolResult result = dispatcher.dispatch("getInventoryRisk", "not-json");

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isNotNull();
    }

    private ExpiryAlert expiryAlert(final Long lotId, final int daysUntilExpiry, final String level) {
        final ExpiryAlert alert = new ExpiryAlert();
        alert.setLotId(lotId);
        alert.setProductId(lotId);
        alert.setDaysUntilExpiry(daysUntilExpiry);
        alert.setAlertLevel(level);
        alert.setExpiryDate(LocalDate.parse("2026-06-30"));
        alert.setQuantity(10);
        return alert;
    }
}
