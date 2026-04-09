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
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryQueryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final LotRepository lotRepository;

    public List<InventoryDTO> getAllInventory() {
        return inventoryRepository.findAll().stream().map(this::toDTO).toList();
    }

    public List<InventoryDTO> getInventoryByProduct(final Long productId) {
        return inventoryRepository.findByProductId(productId).stream().map(this::toDTO).toList();
    }

    public List<InventoryDTO> getInventoryByLocation(final Long locationId) {
        return inventoryRepository.findByLocationId(locationId).stream().map(this::toDTO).toList();
    }

    public List<InventoryDTO> getInventoryByLot(final Long lotId) {
        return inventoryRepository.findByLotId(lotId).stream().map(this::toDTO).toList();
    }

    public InventoryDTO getInventoryById(final Long id) {
        final Inventory inventory = inventoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found: " + id));
        return toDTO(inventory);
    }

    public List<InventoryTransactionDTO> getTransactionHistory(final Long productId,
                                                               final Long locationId,
                                                               final Long lotId) {
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

        return transactions.stream().map(this::toTransactionDTO).toList();
    }

    public List<InventoryTransactionDTO> getRecentTransactions(final int limit) {
        return transactionRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .limit(Math.max(0, limit))
                .map(this::toTransactionDTO)
                .toList();
    }

    private InventoryDTO toDTO(final Inventory inventory) {
        final Product product = productRepository.findById(inventory.getProductId()).orElse(null);
        final Location location = locationRepository.findById(inventory.getLocationId()).orElse(null);
        final Lot lot = inventory.getLotId() == null ? null : lotRepository.findById(inventory.getLotId()).orElse(null);

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

    private InventoryTransactionDTO toTransactionDTO(final InventoryTransaction transaction) {
        final Product product = productRepository.findById(transaction.getProductId()).orElse(null);
        final Location location = locationRepository.findById(transaction.getLocationId()).orElse(null);
        final Lot lot = transaction.getLotId() == null ? null : lotRepository.findById(transaction.getLotId()).orElse(null);

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
}
