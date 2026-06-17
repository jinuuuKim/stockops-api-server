package com.stockops.service.ai;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the intraday forecast proposal scheduler.
 *
 * <p>Disabled by default so it never fires live forecasts (or external AI calls) in local/test
 * environments. Forecast tuning (history window, trailing average, lead-time lookback) is shared
 * with {@link AIRecommendationProperties}; this class only carries intraday-specific knobs.
 *
 * @author StockOps Team
 * @since 2.4
 */
@ConfigurationProperties(prefix = "stockops.ai.intraday")
public class IntradayForecastProperties {

    /** Master switch; when false the scheduler bean is not registered. */
    private boolean enabled = false;

    /** Cron for the slot runs, in {@link #businessZone}. Default: 10:00 and 15:00. */
    private String slotsCron = "0 0 10,15 * * ?";

    private String businessZone = "Asia/Seoul";

    /** Days a proposal stays approvable/rejectable from its run instant; afterwards history-only. */
    private int actionableDays = 3;

    /** Cron for the daily sweep that flips past-window open proposals to EXPIRED, in {@link #businessZone}. */
    private String expiryCron = "0 30 0 * * ?";

    /** Optional forecast model id of a registered ForecastModel; blank uses the default statistical model. */
    private String forecastModel = "";

    private Notify notify = new Notify();

    /**
     * Teams webhook notification settings for intraday proposals.
     */
    public static class Notify {

        /** When true, each slot pushes a summary of fresh proposals to the role webhook channels. */
        private boolean enabled = false;

        /** Only proposals with recommendedQuantity at or above this are included in the push. */
        private int minRecommendedQuantity = 1;

        /** Maximum proposal lines included in a single notification. */
        private int maxItems = 10;

        /** Target roles for routing; empty broadcasts to every enabled role channel. */
        private List<String> targetRoles = List.of();

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(final boolean enabled) {
            this.enabled = enabled;
        }

        public int getMinRecommendedQuantity() {
            return this.minRecommendedQuantity;
        }

        public void setMinRecommendedQuantity(final int minRecommendedQuantity) {
            this.minRecommendedQuantity = minRecommendedQuantity;
        }

        public int getMaxItems() {
            return this.maxItems;
        }

        public void setMaxItems(final int maxItems) {
            this.maxItems = maxItems;
        }

        public List<String> getTargetRoles() {
            return this.targetRoles;
        }

        public void setTargetRoles(final List<String> targetRoles) {
            this.targetRoles = targetRoles;
        }
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public String getSlotsCron() {
        return this.slotsCron;
    }

    public void setSlotsCron(final String slotsCron) {
        this.slotsCron = slotsCron;
    }

    public String getBusinessZone() {
        return this.businessZone;
    }

    public void setBusinessZone(final String businessZone) {
        this.businessZone = businessZone;
    }

    public int getActionableDays() {
        return this.actionableDays;
    }

    public void setActionableDays(final int actionableDays) {
        this.actionableDays = actionableDays;
    }

    public String getExpiryCron() {
        return this.expiryCron;
    }

    public void setExpiryCron(final String expiryCron) {
        this.expiryCron = expiryCron;
    }

    public String getForecastModel() {
        return this.forecastModel;
    }

    public void setForecastModel(final String forecastModel) {
        this.forecastModel = forecastModel;
    }

    public Notify getNotify() {
        return this.notify;
    }

    public void setNotify(final Notify notify) {
        this.notify = notify;
    }
}
