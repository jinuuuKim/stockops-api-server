package com.stockops.notification.lowstock;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically scans inventory and dispatches low-stock Teams cards.
 *
 * <p>Registered unless {@code stockops.notification.low-stock.enabled=false}. {@code @SchedulerLock}
 * keeps a single load-balanced instance running each cycle. The actual send frequency is bounded by
 * the per-scope cooldown in {@link LowStockWebhookService}, not by this scan interval. Exceptions are
 * logged and swallowed so the scheduler thread survives a failed run.
 *
 * @author StockOps Team
 * @since 2.5
 */
@Component
@ConditionalOnProperty(name = "stockops.notification.low-stock.enabled", havingValue = "true",
        matchIfMissing = true)
public class LowStockAlertScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(LowStockAlertScheduler.class);

    private final LowStockWebhookService lowStockWebhookService;

    public LowStockAlertScheduler(final LowStockWebhookService lowStockWebhookService) {
        this.lowStockWebhookService = lowStockWebhookService;
    }

    /**
     * Runs one low-stock scan + dispatch cycle.
     */
    @Scheduled(
            cron = "${stockops.notification.low-stock.scan-cron:0 */30 * * * ?}",
            zone = "${stockops.notification.low-stock.business-zone:Asia/Seoul}")
    @SchedulerLock(name = "lowStockWebhookScan", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void scan() {
        try {
            lowStockWebhookService.scanAndNotify();
        } catch (final RuntimeException e) {
            LOGGER.error("Low-stock webhook scan failed: {}", e.getMessage(), e);
        }
    }
}
