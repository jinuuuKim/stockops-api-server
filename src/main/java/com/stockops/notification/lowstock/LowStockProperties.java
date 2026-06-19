package com.stockops.notification.lowstock;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the low-stock Teams webhook notifier.
 *
 * <p>Defaults are tuned so the channel stays quiet: a scope (warehouse or global) is notified at
 * most once per {@link #cooldown}. The scan runs frequently only to be responsive once the cooldown
 * has elapsed — the cooldown, not the scan interval, controls how often a card can be sent.
 *
 * @author StockOps Team
 * @since 2.5
 */
@ConfigurationProperties(prefix = "stockops.notification.low-stock")
public class LowStockProperties {

    /** Master switch for the scheduled low-stock webhook scan. */
    private boolean enabled = true;

    /** Cron for the scan (business zone). The cooldown gate decides whether a card is actually sent. */
    private String scanCron = "0 */30 * * * ?";

    /** Time zone for {@link #scanCron}. */
    private String businessZone = "Asia/Seoul";

    /** Minimum time between cards for the same scope (warehouse or global). */
    private Duration cooldown = Duration.ofHours(6);

    /**
     * System-wide escalation fires when the share of safety-stock-tracked SKUs that are low reaches
     * this fraction (0.0–1.0). A fraction (rather than an absolute count) scales with catalog size.
     */
    private double adminLowRatio = 0.5;

    /** Role whose webhook channels receive the system-wide escalation (최고관리자). */
    private String adminRole = "ADMIN";

    /** Maximum number of SKU lines listed in a single warehouse card before truncation. */
    private int maxItemsPerCard = 15;

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public String getScanCron() {
        return this.scanCron;
    }

    public void setScanCron(final String scanCron) {
        this.scanCron = scanCron;
    }

    public String getBusinessZone() {
        return this.businessZone;
    }

    public void setBusinessZone(final String businessZone) {
        this.businessZone = businessZone;
    }

    public Duration getCooldown() {
        return this.cooldown;
    }

    public void setCooldown(final Duration cooldown) {
        this.cooldown = cooldown;
    }

    public double getAdminLowRatio() {
        return this.adminLowRatio;
    }

    public void setAdminLowRatio(final double adminLowRatio) {
        this.adminLowRatio = adminLowRatio;
    }

    public String getAdminRole() {
        return this.adminRole;
    }

    public void setAdminRole(final String adminRole) {
        this.adminRole = adminRole;
    }

    public int getMaxItemsPerCard() {
        return this.maxItemsPerCard;
    }

    public void setMaxItemsPerCard(final int maxItemsPerCard) {
        this.maxItemsPerCard = maxItemsPerCard;
    }
}
