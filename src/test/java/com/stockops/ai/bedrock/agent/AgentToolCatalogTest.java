package com.stockops.ai.bedrock.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Converse tool catalog stays in sync with the dispatcher's supported tools.
 *
 * @author StockOps Team
 * @since 2.4
 */
class AgentToolCatalogTest {

    private static final List<String> EXPECTED_TOOLS = List.of(
            "searchInventory",
            "getInventoryRisk",
            "getForecastRecommendation",
            "getSensorAnomalies",
            "getPurchaseOrderDelaySummary",
            "getProphetForecast",
            "getRecentSensorReadings",
            "getExpiringLots",
            "getInventoryByLocation",
            "getRecommendationExplanationContext",
            "getCenterInventorySummary",
            "getInventoryTransactionHistory",
            "getAbcXyzClassification",
            "getInventoryTurnover",
            "getFillRate",
            "getExpiryWaste",
            "generateRecommendationSnapshot",
            "createAISuggestionDraft");

    @Test
    void catalogExposesEveryDispatcherTool() {
        final AgentToolCatalog catalog = new AgentToolCatalog();

        assertThat(catalog.toolNames())
                .containsExactlyInAnyOrderElementsOf(EXPECTED_TOOLS);
        assertThat(catalog.toolConfiguration().tools()).hasSize(EXPECTED_TOOLS.size());
    }

    @Test
    void everyToolHasNameDescriptionAndSchema() {
        final AgentToolCatalog catalog = new AgentToolCatalog();

        catalog.toolConfiguration().tools().forEach(tool -> {
            assertThat(tool.toolSpec().name()).isNotBlank();
            assertThat(tool.toolSpec().description()).isNotBlank();
            assertThat(tool.toolSpec().inputSchema()).isNotNull();
            assertThat(tool.toolSpec().inputSchema().json()).isNotNull();
        });
    }
}
