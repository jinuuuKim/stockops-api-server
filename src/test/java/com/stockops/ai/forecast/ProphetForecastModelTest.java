package com.stockops.ai.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.stockops.entity.ai.AIRecommendationStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProphetForecastModelTest {

    @Mock
    private AiForecastClient aiForecastClient;

    @Mock
    private ForecastModel statisticalFallback;

    private ProphetForecastModel prophetForecastModel;

    private ForecastContext buildContext(final int currentStock, final int safetyStock, final int leadTimeDays) {
        return new ForecastContext(
                1L, 1L, 1L, LocalDate.of(2026, 6, 1),
                currentStock, safetyStock,
                List.of(),
                ForecastContext.LeadTimeInfo.defaultFor(leadTimeDays),
                new ForecastContext.ForecastParameters(7, 4, 7, 28,
                        BigDecimal.valueOf(0.7), BigDecimal.valueOf(0.3)));
    }

    @BeforeEach
    void setUp() {
        prophetForecastModel = new ProphetForecastModel(aiForecastClient, statisticalFallback);
    }

    @Test
    void computeForecast_recommendsReorderWhenStockBelowLeadTimeDemandPlusSafety() {
        final var forecastPoints = List.of(
                new AiForecastClient.AiForecastResponse.ForecastPoint("2026-06-01", 10.0),
                new AiForecastClient.AiForecastResponse.ForecastPoint("2026-06-02", 10.0),
                new AiForecastClient.AiForecastResponse.ForecastPoint("2026-06-03", 10.0),
                new AiForecastClient.AiForecastResponse.ForecastPoint("2026-06-04", 10.0),
                new AiForecastClient.AiForecastResponse.ForecastPoint("2026-06-05", 10.0),
                new AiForecastClient.AiForecastResponse.ForecastPoint("2026-06-06", 10.0),
                new AiForecastClient.AiForecastResponse.ForecastPoint("2026-06-07", 10.0));
        when(aiForecastClient.getForecast(1L, 7))
                .thenReturn(new AiForecastClient.AiForecastResponse(1L, 7, forecastPoints));

        final ForecastContext context = buildContext(5, 10, 2);
        final ForecastResult result = prophetForecastModel.computeForecast(context);

        assertThat(result.status()).isEqualTo(AIRecommendationStatus.READY_FOR_APPROVAL);
        assertThat(result.recommendedQuantity()).isPositive();
        assertThat(result.modelVersion()).isEqualTo("prophet");
    }

    @Test
    void computeForecast_fallsBackToStatisticalWhenProphetReturnsNull() {
        when(aiForecastClient.getForecast(1L, 7)).thenReturn(null);
        final ForecastResult statisticalResult = new ForecastResult(
                BigDecimal.valueOf(5), BigDecimal.ZERO, BigDecimal.valueOf(5),
                35, 2, 10, 28, 10, false, 5,
                AIRecommendationStatus.READY_FOR_APPROVAL, "statistical", "statistical");
        when(statisticalFallback.computeForecast(org.mockito.ArgumentMatchers.any()))
                .thenReturn(statisticalResult);

        final ForecastContext context = buildContext(5, 10, 2);
        final ForecastResult result = prophetForecastModel.computeForecast(context);

        assertThat(result.explanationSummary()).startsWith("Prophet fallback:");
        assertThat(result.modelVersion()).isEqualTo("prophet");
    }

    @Test
    void computeForecast_returnsNoActionWhenStockSufficient() {
        final var forecastPoints = List.of(
                new AiForecastClient.AiForecastResponse.ForecastPoint("2026-06-01", 1.0));
        when(aiForecastClient.getForecast(1L, 7))
                .thenReturn(new AiForecastClient.AiForecastResponse(1L, 7, forecastPoints));

        final ForecastContext context = buildContext(1000, 10, 1);
        final ForecastResult result = prophetForecastModel.computeForecast(context);

        assertThat(result.status()).isEqualTo(AIRecommendationStatus.NO_ACTION);
        assertThat(result.recommendedQuantity()).isZero();
    }
}
