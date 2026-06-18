package com.stockops.service;

import com.stockops.dto.InventoryDTO;
import com.stockops.dto.InventoryTransactionDTO;
import com.stockops.entity.Inventory;
import com.stockops.entity.InventoryTransaction;
import com.stockops.entity.Lot;
import com.stockops.entity.Location;
import com.stockops.entity.Product;
import com.stockops.exception.ResourceNotFoundException;
import com.stockops.repository.InventoryRepository;
import com.stockops.repository.InventoryTransactionRepository;
import com.stockops.repository.LotRepository;
import com.stockops.repository.LocationRepository;
import com.stockops.repository.ProductRepository;
import com.stockops.security.ScopeGuard;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inventory query service for read-only inventory and transaction lookups.
 *
 * @author StockOps Team
 * @since 1.0
 * @see InventoryRepository
 * @see InventoryTransactionRepository
 */
@Service
@Transactional(readOnly = true)
public class InventoryQueryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final LotRepository lotRepository;
    private final ScopeGuard scopeGuard;

    /**
     * Returns all inventory rows visible to the current scope.
     *
     * @return filtered inventory list
     */
    public List<InventoryDTO> getAllInventory() {
        return toInventoryDtos(scopeGuard.filterByLocationScope(inventoryRepository.findAll(), Inventory::getLocationId));
    }

    /**
     * Returns inventory rows for a product, filtered to the current scope.
     *
     * @param productId product identifier
     * @return filtered inventory list
     */
    public List<InventoryDTO> getInventoryByProduct(final Long productId) {
        return toInventoryDtos(scopeGuard.filterByLocationScope(
                inventoryRepository.findByProductId(productId),
                Inventory::getLocationId));
    }

    /**
     * Returns inventory rows for a location when the location is in scope.
     *
     * @param locationId location identifier
     * @return filtered inventory list, or an empty list when the location is outside scope
     */
    public List<InventoryDTO> getInventoryByLocation(final Long locationId) {
        if (!scopeGuard.canAccessLocation(locationId)) {
            return List.of();
        }
        return toInventoryDtos(inventoryRepository.findByLocationId(locationId));
    }

    /**
     * Returns inventory rows for a lot, filtered to the current scope.
     *
     * @param lotId lot identifier
     * @return filtered inventory list
     */
    public List<InventoryDTO> getInventoryByLot(final Long lotId) {
        return toInventoryDtos(scopeGuard.filterByLocationScope(
                inventoryRepository.findByLotId(lotId),
                Inventory::getLocationId));
    }

    /**
     * Returns a single inventory row and rejects direct access outside scope.
     *
     * @param id inventory identifier
     * @return inventory DTO
     */
    public InventoryDTO getInventoryById(final Long id) {
        final Inventory inventory = inventoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found: " + id));
        scopeGuard.assertLocationAccess(inventory.getLocationId());
        return toDTO(inventory);
    }

    public List<InventoryTransactionDTO> getTransactionHistory(final Long productId,
                                                               final Long locationId,
                                                               final Long lotId) {
        if (locationId != null && !scopeGuard.canAccessLocation(locationId)) {
            return List.of();
        }

        final List<InventoryTransaction> transactions;
        if (productId != null) {
            transactions = transactionRepository.findByProductIdOrderByCreatedAtDesc(productId);
        } else if (locationId != null) {
            transactions = transactionRepository.findByLocationIdOrderByCreatedAtDesc(locationId);
        } else if (lotId != null) {
            transactions = transactionRepository.findByLotIdOrderByCreatedAtDesc(lotId);
        } else {
            transactions = transactionRepository.findAll();
            transactions.sort(Comparator.comparing(
                    InventoryTransaction::getCreatedAt,
                    Comparator.nullsLast(Comparator.reverseOrder())));
        }

        return toTransactionDtos(
                scopeGuard.filterByLocationScope(transactions, InventoryTransaction::getLocationId));
    }

    /**
     * Returns the most recent visible inventory transactions.
     *
     * @param limit maximum number of rows to return
     * @return filtered recent transactions
     */
    public List<InventoryTransactionDTO> getRecentTransactions(final int limit) {
        final List<InventoryTransaction> visible = scopeGuard.filterByLocationScope(
                        transactionRepository.findTop50ByOrderByCreatedAtDesc(),
                        InventoryTransaction::getLocationId)
                .stream()
                .limit(Math.max(0, limit))
                .toList();
        return toTransactionDtos(visible);
    }

    private static final int SEARCH_LIMIT = 20;

    /**
     * Free-text inventory search by product name, barcode, or lot number — so the assistant can
     * resolve a user's natural query (which rarely includes numeric ids) into concrete products
     * and lots with their scope-visible stock.
     *
     * <p>Lot numbers are stored with a {@code LOT-} prefix (e.g. {@code LOT-9900...-260304-01}).
     * A user often types the lot with a {@code LOT} or {@code LOT-} prefix, so the query is
     * normalized into several lot-number terms (raw, prefix-stripped, and {@code LOT-}-prefixed)
     * before matching. Products referenced by matched lots are included even when the raw query
     * does not match their name/barcode.
     *
     * @param query free-text keyword (product name, barcode, or lot number)
     * @return matched products (with scope-filtered stock summary) and lots
     */
    public Map<String, Object> searchInventory(final String query) {
        final String q = query == null ? "" : query.trim();
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", q);
        if (q.isBlank()) {
            result.put("products", List.of());
            result.put("lots", List.of());
            result.put("productMatchCount", 0);
            result.put("lotMatchCount", 0);
            result.put("message", "검색어가 비어 있습니다.");
            return result;
        }

        final Pageable limit = PageRequest.of(0, SEARCH_LIMIT);

        // Lot search across LOT / LOT- normalized terms (dedup by lot id, capped).
        final Map<Long, Lot> lotHits = new LinkedHashMap<>();
        for (final String term : lotSearchTerms(q)) {
            for (final Lot lot : lotRepository.searchByLotNumber(term, limit)) {
                lotHits.putIfAbsent(lot.getId(), lot);
            }
            if (lotHits.size() >= SEARCH_LIMIT) {
                break;
            }
        }

        // Product search by name/barcode, plus the products referenced by matched lots.
        final Map<Long, Product> productHits = new LinkedHashMap<>();
        for (final Product product : productRepository.searchByNameOrBarcode(q, limit)) {
            productHits.putIfAbsent(product.getId(), product);
        }
        for (final Lot lot : lotHits.values()) {
            if (!productHits.containsKey(lot.getProductId())) {
                productRepository.findByIdAndDeletedFalse(lot.getProductId())
                        .ifPresent(product -> productHits.putIfAbsent(product.getId(), product));
            }
        }

        final List<Map<String, Object>> products = new ArrayList<>();
        for (final Product product : productHits.values()) {
            final List<InventoryDTO> rows = getInventoryByProduct(product.getId());
            int onHand = 0;
            int available = 0;
            final Set<Long> locations = new HashSet<>();
            for (final InventoryDTO row : rows) {
                onHand += row.quantity();
                available += row.quantity() - row.reservedQuantity();
                locations.add(row.locationId());
            }
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("productId", product.getId());
            entry.put("name", product.getName());
            entry.put("barcode", product.getBarcode());
            entry.put("category", product.getCategory());
            entry.put("totalOnHand", onHand);
            entry.put("totalAvailable", available);
            entry.put("locationCount", locations.size());
            products.add(entry);
        }

        final List<Map<String, Object>> lots = new ArrayList<>();
        for (final Lot lot : lotHits.values()) {
            final Product product = productHits.get(lot.getProductId());
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("lotId", lot.getId());
            entry.put("lotNumber", lot.getLotNumber());
            entry.put("productId", lot.getProductId());
            entry.put("productName", product == null ? null : product.getName());
            entry.put("quantity", lot.getQuantity() == null ? 0 : lot.getQuantity());
            entry.put("status", lot.getStatus() == null ? null : lot.getStatus().name());
            entry.put("expiryDate", lot.getExpiryDate());
            lots.add(entry);
        }

        result.put("products", products);
        result.put("lots", lots);
        result.put("productMatchCount", products.size());
        result.put("lotMatchCount", lots.size());
        return result;
    }

    /**
     * Builds the lot-number search terms for a query, normalizing {@code LOT}/{@code LOT-} prefixes
     * so a user-typed {@code LOT12345} or {@code LOT-12345} matches the stored {@code LOT-...} form.
     */
    private List<String> lotSearchTerms(final String query) {
        final Set<String> terms = new LinkedHashSet<>();
        terms.add(query);
        final String upper = query.toUpperCase(Locale.ROOT);
        if (upper.startsWith("LOT-")) {
            terms.add(query.substring(4));
        } else if (upper.startsWith("LOT")) {
            final String rest = query.substring(3);
            terms.add(rest);
            terms.add("LOT-" + rest);
        }
        terms.removeIf(String::isBlank);
        return new ArrayList<>(terms);
    }

    private List<InventoryDTO> toInventoryDtos(final List<Inventory> inventory) {
        if (inventory.isEmpty()) {
            return List.of();
        }
        // Batch-fetch related entities once per type (3 queries total) instead of 3 per row,
        // which previously produced an N+1 query explosion during DTO mapping.
        final Map<Long, Product> products =
                productsByIds(inventory.stream().map(Inventory::getProductId).toList());
        final Map<Long, Location> locations =
                locationsByIds(inventory.stream().map(Inventory::getLocationId).toList());
        final Map<Long, Lot> lots =
                lotsByIds(inventory.stream().map(Inventory::getLotId).toList());
        return inventory.stream()
                .map(row -> buildInventoryDTO(
                        row,
                        products.get(row.getProductId()),
                        locations.get(row.getLocationId()),
                        row.getLotId() == null ? null : lots.get(row.getLotId())))
                .toList();
    }

    private InventoryDTO toDTO(final Inventory inventory) {
        final Product product = productRepository.findById(inventory.getProductId()).orElse(null);
        final Location location = locationRepository.findById(inventory.getLocationId()).orElse(null);
        final Lot lot = inventory.getLotId() == null ? null : lotRepository.findById(inventory.getLotId()).orElse(null);
        return buildInventoryDTO(inventory, product, location, lot);
    }

    private InventoryDTO buildInventoryDTO(final Inventory inventory, final Product product,
                                           final Location location, final Lot lot) {
        return new InventoryDTO(
                inventory.getId(),
                inventory.getProductId(),
                product == null ? null : product.getBarcode(),
                product == null ? null : product.getName(),
                inventory.getLocationId(),
                location == null ? null : location.getCode(),
                location == null ? null : location.getName(),
                inventory.getLotId(),
                lot == null ? null : lot.getLotNumber(),
                lot == null ? null : lot.getExpiryDate(),
                nullSafeInt(inventory.getQuantity()),
                nullSafeInt(inventory.getReservedQuantity()),
                inventory.getStatus() == null ? "ACTIVE" : inventory.getStatus().name(),
                inventory.getCreatedAt(),
                inventory.getUpdatedAt());
    }

    private List<InventoryTransactionDTO> toTransactionDtos(final List<InventoryTransaction> transactions) {
        if (transactions.isEmpty()) {
            return List.of();
        }
        // Batch-fetch related entities once per type instead of 3 lookups per transaction row.
        final Map<Long, Product> products =
                productsByIds(transactions.stream().map(InventoryTransaction::getProductId).toList());
        final Map<Long, Location> locations =
                locationsByIds(transactions.stream().map(InventoryTransaction::getLocationId).toList());
        final Map<Long, Lot> lots =
                lotsByIds(transactions.stream().map(InventoryTransaction::getLotId).toList());
        return transactions.stream()
                .map(txn -> buildTransactionDTO(
                        txn,
                        products.get(txn.getProductId()),
                        locations.get(txn.getLocationId()),
                        txn.getLotId() == null ? null : lots.get(txn.getLotId())))
                .toList();
    }

    private InventoryTransactionDTO buildTransactionDTO(final InventoryTransaction transaction,
                                                        final Product product, final Location location,
                                                        final Lot lot) {
        return new InventoryTransactionDTO(
                transaction.getId(),
                transaction.getType(),
                transaction.getProductId(),
                product == null ? null : product.getName(),
                transaction.getLocationId(),
                location == null ? null : location.getCode(),
                transaction.getLotId(),
                lot == null ? null : lot.getLotNumber(),
                nullSafeInt(transaction.getQuantity()),
                nullSafeInt(transaction.getBeforeQuantity()),
                nullSafeInt(transaction.getAfterQuantity()),
                transaction.getReferenceId(),
                transaction.getType(),
                transaction.getCreatedBy(),
                transaction.getCreatedAt());
    }

    private int nullSafeInt(final Integer value) {
        return value == null ? 0 : value;
    }

    private Map<Long, Product> productsByIds(final List<Long> ids) {
        final Set<Long> distinct = distinctNonNull(ids);
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return productRepository.findAllById(distinct).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    private Map<Long, Location> locationsByIds(final List<Long> ids) {
        final Set<Long> distinct = distinctNonNull(ids);
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return locationRepository.findAllById(distinct).stream()
                .collect(Collectors.toMap(Location::getId, Function.identity()));
    }

    private Map<Long, Lot> lotsByIds(final List<Long> ids) {
        final Set<Long> distinct = distinctNonNull(ids);
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return lotRepository.findAllById(distinct).stream()
                .collect(Collectors.toMap(Lot::getId, Function.identity()));
    }

    private static Set<Long> distinctNonNull(final List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }

    public InventoryQueryService(final InventoryRepository inventoryRepository, final InventoryTransactionRepository transactionRepository, final ProductRepository productRepository, final LocationRepository locationRepository, final LotRepository lotRepository, final ScopeGuard scopeGuard) {
        this.inventoryRepository = inventoryRepository;
        this.transactionRepository = transactionRepository;
        this.productRepository = productRepository;
        this.locationRepository = locationRepository;
        this.lotRepository = lotRepository;
        this.scopeGuard = scopeGuard;
    }
}
