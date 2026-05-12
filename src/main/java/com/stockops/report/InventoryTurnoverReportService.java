package com.stockops.report;

import com.stockops.dto.InventoryTurnoverDTO;
import com.stockops.entity.InventoryTransaction;
import com.stockops.entity.Location;
import com.stockops.entity.Product;
import com.stockops.entity.Warehouse;
import com.stockops.repository.InventoryRepository;
import com.stockops.repository.InventoryTransactionRepository;
import com.stockops.repository.LocationRepository;
import com.stockops.repository.ProductRepository;
import com.stockops.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service that calculates inventory turnover rates per product for a given period.
 *
 * <p>Turnover Rate = COGS / Average Inventory Value, annualized when period &lt; 365 days.
 * COGS = total outbound quantity × product default price.
 * Average Inventory = (beginning inventory qty + ending inventory qty) / 2 × unit price.
 *
 * @author StockOps Team
 * @since 2.0
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryTurnoverReportService {

    private static final String OUTBOUND_TYPE = "OUTBOUND";
    private static final BigDecimal DAYS_PER_YEAR = BigDecimal.valueOf(365);

    private final InventoryTransactionRepository transactionRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final LocationRepository locationRepository;

    /**
     * Generates inventory turnover report for all products.
     *
     * @param startDate period start (ISO date, treated as start of day UTC)
     * @param endDate period end (ISO date, treated as end of day UTC)
     * @return list of turnover DTOs sorted by turnoverRate descending
     */
    public List<InventoryTurnoverDTO> generateReport(LocalDate startDate, LocalDate endDate) {
        Instant startInstant = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endInstant = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        int periodDays = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;

        List<Product> products = productRepository.findAll();
        List<InventoryTurnoverDTO> results = new ArrayList<>();

        for (Product product : products) {
            InventoryTurnoverDTO dto = calculateForProduct(product, startInstant, endInstant, periodDays);
            if (dto != null) {
                results.add(dto);
            }
        }

        results.sort((a, b) -> b.turnoverRate().compareTo(a.turnoverRate()));
        return results;
    }

    /**
     * Generates inventory turnover report filtered by center.
     *
     * @param startDate period start (ISO date)
     * @param endDate period end (ISO date)
     * @param centerId center identifier to filter warehouses/locations
     * @return list of turnover DTOs sorted by turnoverRate descending
     */
    public List<InventoryTurnoverDTO> generateReport(LocalDate startDate, LocalDate endDate, Long centerId) {
        Instant startInstant = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endInstant = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        int periodDays = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;

        List<Warehouse> warehouses = warehouseRepository.findByCenterId(centerId);
        if (warehouses.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> warehouseIds = warehouses.stream().map(Warehouse::getId).toList();
        List<Location> locations = locationRepository.findByWarehouseIdIn(warehouseIds);
        if (locations.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> locationIds = locations.stream().map(Location::getId).toList();

        // Get outbound quantities grouped by product for these locations
        List<Object[]> outboundSums = transactionRepository
                .sumQuantityByLocationIdsAndTypeAndCreatedAtBetween(locationIds, OUTBOUND_TYPE, startInstant, endInstant);
        Map<Long, Long> cogsQuantityByProduct = outboundSums.stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));

        // Get current inventory grouped by product for these locations
        List<Object[]> inventorySums = inventoryRepository.sumQuantityByLocationIdsIn(locationIds);
        Map<Long, Long> endingInventoryByProduct = inventorySums.stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));

        // Get beginning inventory: current + outbound in period (reverse calculation)
        // beginning = ending + outbound_during_period (since outbound reduces inventory)
        Map<Long, Long> beginningInventoryByProduct = endingInventoryByProduct.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue() + cogsQuantityByProduct.getOrDefault(entry.getKey(), 0L)
                ));

        // Also include products that had outbound but zero ending inventory
        for (Map.Entry<Long, Long> entry : cogsQuantityByProduct.entrySet()) {
            beginningInventoryByProduct.putIfAbsent(entry.getKey(), entry.getValue());
            endingInventoryByProduct.putIfAbsent(entry.getKey(), 0L);
        }

        // Load products
        List<Long> productIds = new ArrayList<>(beginningInventoryByProduct.keySet());
        Map<Long, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<InventoryTurnoverDTO> results = new ArrayList<>();
        for (Long productId : productIds) {
            Product product = productMap.get(productId);
            if (product == null) {
                continue;
            }
            long beginningQty = beginningInventoryByProduct.getOrDefault(productId, 0L);
            long endingQty = endingInventoryByProduct.getOrDefault(productId, 0L);
            long outboundQty = cogsQuantityByProduct.getOrDefault(productId, 0L);

            InventoryTurnoverDTO dto = buildDTO(product, (int) beginningQty, (int) endingQty,
                    (int) outboundQty, periodDays);
            if (dto != null) {
                results.add(dto);
            }
        }

        results.sort((a, b) -> b.turnoverRate().compareTo(a.turnoverRate()));
        return results;
    }

    private InventoryTurnoverDTO calculateForProduct(Product product, Instant start, Instant end, int periodDays) {
        long outboundQty = transactionRepository.sumQuantityByProductIdAndTypeAndCreatedAtBetween(
                product.getId(), OUTBOUND_TYPE, start, end);

        long endingQty = inventoryRepository.sumQuantityByProductId(product.getId());

        // Reverse-calculate beginning inventory: beginning = ending + outbound during period
        long beginningQty = endingQty + outboundQty;

        return buildDTO(product, (int) beginningQty, (int) endingQty, (int) outboundQty, periodDays);
    }

    private InventoryTurnoverDTO buildDTO(Product product, int beginningQty, int endingQty,
                                          int outboundQty, int periodDays) {
        int averageQty = (beginningQty + endingQty) / 2;

        BigDecimal unitPrice = product.getDefaultPrice();
        BigDecimal cogs = unitPrice.multiply(BigDecimal.valueOf(outboundQty));
        BigDecimal averageInventoryValue = unitPrice.multiply(BigDecimal.valueOf(averageQty));

        BigDecimal turnoverRate;
        if (averageQty == 0) {
            // Filter out products with zero average inventory per spec
            return null;
        }

        BigDecimal rawRate = cogs.divide(averageInventoryValue, 4, RoundingMode.HALF_UP);

        // Annualize if period < 365 days
        if (periodDays < 365) {
            BigDecimal annualizationFactor = DAYS_PER_YEAR.divide(BigDecimal.valueOf(periodDays), 4, RoundingMode.HALF_UP);
            turnoverRate = rawRate.multiply(annualizationFactor).setScale(2, RoundingMode.HALF_UP);
        } else {
            turnoverRate = rawRate.setScale(2, RoundingMode.HALF_UP);
        }

        return new InventoryTurnoverDTO(
                product.getId(),
                product.getName(),
                product.getBarcode(),
                unitPrice,
                beginningQty,
                endingQty,
                averageQty,
                cogs.setScale(2, RoundingMode.HALF_UP),
                turnoverRate,
                periodDays
        );
    }
}
