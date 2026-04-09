package com.stockops.service;

import com.stockops.dto.DashboardSummaryDTO;
import com.stockops.repository.ExpiryAlertRepository;
import com.stockops.repository.InboundRepository;
import com.stockops.repository.InventoryRepository;
import com.stockops.repository.InventoryTransactionRepository;
import com.stockops.repository.OutboundRepository;
import com.stockops.repository.ProductRepository;
import com.stockops.repository.StockAdjustmentRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard aggregation service.
 *
 * @author StockOps Team
 * @since 1.0
 * @see ProductRepository
 * @see InventoryRepository
 * @see InboundRepository
 * @see OutboundRepository
 * @see StockAdjustmentRepository
 * @see ExpiryAlertRepository
 * @see InventoryTransactionRepository
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private static final int LOW_STOCK_THRESHOLD = 10;

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final InboundRepository inboundRepository;
    private final OutboundRepository outboundRepository;
    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final ExpiryAlertRepository expiryAlertRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;

    /**
     * Builds the dashboard summary.
     *
     * @return dashboard summary DTO
     */
    public DashboardSummaryDTO getSummary() {
        return new DashboardSummaryDTO(
                calculateTotalProducts(),
                calculateTotalInventoryQuantity(),
                calculateTodayInboundCount(),
                calculateTodayOutboundCount(),
                calculateLowStockCount(),
                calculatePendingCycleCounts(),
                calculateCriticalExpiryCount(),
                calculateWarningExpiryCount(),
                calculateRecentTransactionCount());
    }

    /**
     * Counts registered products.
     *
     * @return product count
     */
    public long calculateTotalProducts() {
        return productRepository.count();
    }

    /**
     * Sums available inventory quantity across all rows.
     *
     * @return total available quantity
     */
    public long calculateTotalInventoryQuantity() {
        return inventoryRepository.sumInventoryQuantity();
    }

    /**
     * Counts inbound headers created today.
     *
     * @return today's inbound count
     */
    public long calculateTodayInboundCount() {
        return inboundRepository.countByInboundDate(LocalDate.now());
    }

    /**
     * Counts outbound headers created today.
     *
     * @return today's outbound count
     */
    public long calculateTodayOutboundCount() {
        return outboundRepository.countByOutboundDate(LocalDate.now());
    }

    /**
     * Counts inventory rows whose available quantity is below the dashboard threshold.
     *
     * @return low-stock row count
     */
    public long calculateLowStockCount() {
        return inventoryRepository.countLowStockItems(LOW_STOCK_THRESHOLD);
    }

    /**
     * Counts pending review items using the existing stock adjustment queue.
     *
     * @return pending cycle count items
     */
    public long calculatePendingCycleCounts() {
        return stockAdjustmentRepository.countByStatus("PENDING");
    }

    /**
     * Counts active critical expiry alerts.
     *
     * @return critical expiry alert count
     */
    public long calculateCriticalExpiryCount() {
        return expiryAlertRepository.countByAlertLevelAndAcknowledgedFalse("CRITICAL");
    }

    /**
     * Counts active warning expiry alerts.
     *
     * @return warning expiry alert count
     */
    public long calculateWarningExpiryCount() {
        return expiryAlertRepository.countByAlertLevelAndAcknowledgedFalse("WARNING");
    }

    /**
     * Counts transactions created in the last 7 days.
     *
     * @return recent transaction count
     */
    public long calculateRecentTransactionCount() {
        final Instant now = Instant.now();
        final Instant start = now.minus(7, ChronoUnit.DAYS);
        return inventoryTransactionRepository.countByCreatedAtBetween(start, now);
    }
}
