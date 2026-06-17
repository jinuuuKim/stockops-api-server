package com.stockops.ai.forecast;

import com.stockops.entity.ai.AIRecommendationStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/**
 * Stub adapter for future external AI forecast models (e.g. OpenAI, etc.).
 * <p>
 * Returns a placeholder result with {@code modelVersion = "external-stub"}.
 * Does NOT call any live API. Replace the body with a real adapter when
 * an external model is integrated.
 *
 * @author StockOps Team
 * @since 2.0
 */
@Service("externalForecastModel")
public class ExternalAIForecastAdapter implements ForecastModel {

    private static final String MODEL_ID = "external";

    @Override
    public String getModelId() {
        return MODEL_ID;
    }

    @Override
    public ForecastResult computeForecast(final ForecastContext context) {
        return new ForecastResult(
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                0,
                context.leadTimeInfo().resolvedLeadTimeDays(),
                0,
                context.parameters().forecastHistoryDays(),
                0,
                true,
                0,
                AIRecommendationStatus.INSUFFICIENT_HISTORY,
                "External AI model not yet integrated; placeholder result.",
                "external-stub");
    }
}
