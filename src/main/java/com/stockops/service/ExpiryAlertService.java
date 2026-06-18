package com.stockops.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.stockops.entity.ExpiryAlert;
import com.stockops.entity.Lot;
import com.stockops.repository.ExpiryAlertRepository;
import com.stockops.repository.InventoryRepository;
import com.stockops.repository.LotRepository;
import com.stockops.config.MetricsConfig;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calculates daily expiry alerts for active lots with remaining inventory.
 * Alert levels are bucketed into D-30, D-14, D-7, and D-1 severity bands.
 *
 * @author StockOps Team
 * @since 1.0
 * @see ExpiryAlertRepository
 * @see LotRepository
 * @see InventoryRepository
 */
@Service
public class ExpiryAlertService {

    private static final int INFO_THRESHOLD_DAYS = 30;
    private static final int NOTICE_THRESHOLD_DAYS = 14;
    private static final int WARNING_THRESHOLD_DAYS = 7;
    private static final int CRITICAL_THRESHOLD_DAYS = 1;

    private final ExpiryAlertRepository expiryAlertRepository;
    private final LotRepository lotRepository;
    private final InventoryRepository inventoryRepository;
    private final MetricsConfig metricsConfig;

    /**
     * Recalculates near-expiry alerts once per day at 01:00.
     * Existing unacknowledged alerts are replaced so the alert list always reflects current stock.
     */
    @Scheduled(cron = "0 0 1 * * ?")
    @SchedulerLock(name = "expiryAlertCalculation", lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
    @Transactional
    public void calculateExpiryAlerts() {
        log.info("Starting expiry alert calculation");

        final List<ExpiryAlert> oldAlerts = expiryAlertRepository.findByAcknowledgedFalse();
        if (!oldAlerts.isEmpty()) {
            expiryAlertRepository.deleteAll(oldAlerts);
        }

        final LocalDate today = LocalDate.now();
        final List<Lot> lotsWithExpiry = lotRepository.findActiveLotsWithExpiry();

        // Select lots within the alerting window first, then resolve their on-hand stock in a single
        // grouped query (instead of one findByLotId call per lot).
        final List<Lot> candidateLots = new ArrayList<>();
        for (final Lot lot : lotsWithExpiry) {
            if (lot.getExpiryDate() == null) {
                continue;
            }
            final long daysUntilExpiry = ChronoUnit.DAYS.between(today, lot.getExpiryDate());
            if (daysUntilExpiry >= 0 && daysUntilExpiry <= INFO_THRESHOLD_DAYS) {
                candidateLots.add(lot);
            }
        }

        if (candidateLots.isEmpty()) {
            log.info("Expiry alert calculation completed");
            return;
        }

        final Map<Long, Integer> quantityByLot = new HashMap<>();
        final List<Long> lotIds = candidateLots.stream().map(Lot::getId).toList();
        for (final Object[] row : inventoryRepository.sumPositiveQuantityByLotIdsIn(lotIds)) {
            quantityByLot.put((Long) row[0], ((Long) row[1]).intValue());
        }

        for (final Lot lot : candidateLots) {
            final int totalQuantity = quantityByLot.getOrDefault(lot.getId(), 0);
            if (totalQuantity <= 0) {
                continue;
            }
            final int daysUntilExpiry = (int) ChronoUnit.DAYS.between(today, lot.getExpiryDate());
            createAlert(lot, daysUntilExpiry, totalQuantity);
        }

        log.info("Expiry alert calculation completed");
    }

    private void createAlert(final Lot lot, final int daysUntilExpiry, final int totalQuantity) {
        final ExpiryAlert alert = new ExpiryAlert();
        alert.setLotId(lot.getId());
        alert.setProductId(lot.getProductId());
        alert.setDaysUntilExpiry(daysUntilExpiry);
        alert.setAlertLevel(determineAlertLevel(daysUntilExpiry));
        alert.setExpiryDate(lot.getExpiryDate());
        alert.setQuantity(totalQuantity);
        alert.setAcknowledged(false);

        expiryAlertRepository.save(alert);
        metricsConfig.recordEscalationAlert(alert.getAlertLevel());

        log.info("Created {} alert for lot {} ({} days until expiry)",
                alert.getAlertLevel(),
                lot.getLotNumber(),
                daysUntilExpiry);
    }

    private String determineAlertLevel(final int daysUntilExpiry) {
        if (daysUntilExpiry <= CRITICAL_THRESHOLD_DAYS) {
            return "CRITICAL";
        }
        if (daysUntilExpiry <= WARNING_THRESHOLD_DAYS) {
            return "WARNING";
        }
        if (daysUntilExpiry <= NOTICE_THRESHOLD_DAYS) {
            return "NOTICE";
        }
        return "INFO";
    }

    private static final Logger log = LoggerFactory.getLogger(ExpiryAlertService.class);

    public ExpiryAlertService(final ExpiryAlertRepository expiryAlertRepository, final LotRepository lotRepository, final InventoryRepository inventoryRepository, final MetricsConfig metricsConfig) {
        this.expiryAlertRepository = expiryAlertRepository;
        this.lotRepository = lotRepository;
        this.inventoryRepository = inventoryRepository;
        this.metricsConfig = metricsConfig;
    }
}
