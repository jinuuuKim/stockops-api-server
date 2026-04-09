package com.stockops.service;

import com.stockops.dto.CompleteCycleCountItemRequest;
import com.stockops.dto.CompleteCycleCountRequest;
import com.stockops.dto.CreateCycleCountRequest;
import com.stockops.dto.CycleCountDTO;
import com.stockops.dto.CycleCountItemDTO;
import com.stockops.entity.CycleCount;
import com.stockops.entity.CycleCountItem;
import com.stockops.entity.CycleCountStatus;
import com.stockops.entity.Inventory;
import com.stockops.exception.InvalidOperationException;
import com.stockops.exception.ResourceNotFoundException;
import com.stockops.repository.CycleCountItemRepository;
import com.stockops.repository.CycleCountRepository;
import com.stockops.repository.InventoryRepository;
import com.stockops.repository.LocationRepository;
import com.stockops.repository.UserRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles creation, execution, and completion of inventory cycle counts.
 * Persists frozen expected quantities so count results can be reviewed without immediately changing stock.
 *
 * @author StockOps Team
 * @since 1.0
 * @see CycleCountRepository
 * @see CycleCountItemRepository
 * @see InventoryRepository
 */
@Service
@RequiredArgsConstructor
public class CycleCountService {

    private final CycleCountRepository cycleCountRepository;
    private final CycleCountItemRepository cycleCountItemRepository;
    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;

    /**
     * Creates a new pending cycle count with the selected inventory rows.
     * Expected quantities are snapshotted from the current inventory balances.
     *
     * @param request cycle count creation payload
     * @param userId authenticated creator identifier
     * @return created cycle count response
     * @throws ResourceNotFoundException when the user, location, or inventory does not exist
     * @throws InvalidOperationException when inventory rows are duplicated or belong to a different location
     */
    @Transactional
    public CycleCountDTO createCycleCount(final CreateCycleCountRequest request, final Long userId) {
        validateUser(userId);
        validateLocation(request.locationId());

        final List<Long> inventoryIds = request.inventoryIds();
        ensureUniqueIdentifiers(inventoryIds, "Inventory ids must be unique");

        final CycleCount cycleCount = new CycleCount();
        cycleCount.setCountDate(request.countDate());
        cycleCount.setStatus(CycleCountStatus.PENDING);
        cycleCount.setLocationId(request.locationId());
        cycleCount.setCreatedBy(userId);

        final CycleCount savedCycleCount = cycleCountRepository.save(cycleCount);
        final List<CycleCountItem> savedItems = cycleCountItemRepository.saveAll(inventoryIds.stream()
                .map(inventoryId -> buildCycleCountItem(savedCycleCount.getId(), request.locationId(), inventoryId))
                .toList());

        return toDto(savedCycleCount, savedItems);
    }

    /**
     * Loads a cycle count with its items.
     *
     * @param id cycle count identifier
     * @return cycle count response
     * @throws ResourceNotFoundException when the cycle count does not exist
     */
    @Transactional(readOnly = true)
    public CycleCountDTO getCycleCount(final Long id) {
        final CycleCount cycleCount = getCycleCountEntity(id);
        return toDto(cycleCount, cycleCountItemRepository.findByCycleCountIdOrderByIdAsc(id));
    }

    /**
     * Starts a pending cycle count.
     *
     * @param id cycle count identifier
     * @param userId authenticated operator identifier
     * @return started cycle count response
     * @throws ResourceNotFoundException when the cycle count or user does not exist
     * @throws InvalidOperationException when the cycle count is not pending
     */
    @Transactional
    public CycleCountDTO startCycleCount(final Long id, final Long userId) {
        validateUser(userId);

        final CycleCount cycleCount = getCycleCountEntity(id);
        if (cycleCount.getStatus() != CycleCountStatus.PENDING) {
            throw new InvalidOperationException("Only pending cycle counts can be started");
        }

        cycleCount.setStatus(CycleCountStatus.IN_PROGRESS);
        final CycleCount savedCycleCount = cycleCountRepository.save(cycleCount);
        return toDto(savedCycleCount, cycleCountItemRepository.findByCycleCountIdOrderByIdAsc(id));
    }

    /**
     * Completes an in-progress cycle count by storing the final counted quantities.
     *
     * @param id cycle count identifier
     * @param request cycle count completion payload
     * @param userId authenticated operator identifier
     * @return completed cycle count response
     * @throws ResourceNotFoundException when the cycle count or user does not exist
     * @throws InvalidOperationException when the cycle count is not in progress or item input is incomplete
     */
    @Transactional
    public CycleCountDTO completeCycleCount(final Long id,
                                            final CompleteCycleCountRequest request,
                                            final Long userId) {
        validateUser(userId);

        final CycleCount cycleCount = getCycleCountEntity(id);
        if (cycleCount.getStatus() != CycleCountStatus.IN_PROGRESS) {
            throw new InvalidOperationException("Only in-progress cycle counts can be completed");
        }

        final List<CycleCountItem> cycleCountItems = cycleCountItemRepository.findByCycleCountIdOrderByIdAsc(id);
        if (cycleCountItems.isEmpty()) {
            throw new InvalidOperationException("Cycle count must include at least one item");
        }

        final Map<Long, CompleteCycleCountItemRequest> requestedItemsById = mapRequestedItems(request.items());
        if (requestedItemsById.size() != cycleCountItems.size()) {
            throw new InvalidOperationException("Final counts must be provided for every cycle count item");
        }

        final Instant countedAt = Instant.now();
        for (CycleCountItem cycleCountItem : cycleCountItems) {
            final CompleteCycleCountItemRequest itemRequest = requestedItemsById.get(cycleCountItem.getId());
            if (itemRequest == null) {
                throw new InvalidOperationException("Final counts must be provided for every cycle count item");
            }

            cycleCountItem.setActualQuantity(itemRequest.actualQuantity());
            cycleCountItem.setVariance(itemRequest.actualQuantity() - nullSafeQuantity(cycleCountItem.getExpectedQuantity()));
            cycleCountItem.setCountedBy(userId);
            cycleCountItem.setCountedAt(countedAt);
            cycleCountItem.setNotes(itemRequest.notes());
        }

        cycleCountItemRepository.saveAll(cycleCountItems);
        cycleCount.setStatus(CycleCountStatus.COMPLETED);
        cycleCount.setCompletedBy(userId);
        cycleCount.setCompletedAt(countedAt);

        final CycleCount savedCycleCount = cycleCountRepository.save(cycleCount);
        return toDto(savedCycleCount, cycleCountItems);
    }

    private CycleCountItem buildCycleCountItem(final Long cycleCountId,
                                               final Long locationId,
                                               final Long inventoryId) {
        final Inventory inventory = inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found"));
        if (!Objects.equals(locationId, inventory.getLocationId())) {
            throw new InvalidOperationException("Inventory must belong to the requested location");
        }

        final CycleCountItem cycleCountItem = new CycleCountItem();
        cycleCountItem.setCycleCountId(cycleCountId);
        cycleCountItem.setInventoryId(inventoryId);
        cycleCountItem.setExpectedQuantity(nullSafeQuantity(inventory.getQuantity()));
        return cycleCountItem;
    }

    private CycleCount getCycleCountEntity(final Long id) {
        return cycleCountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cycle count not found"));
    }

    private void validateLocation(final Long locationId) {
        if (!locationRepository.existsById(locationId)) {
            throw new ResourceNotFoundException("Location not found");
        }
    }

    private void validateUser(final Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found");
        }
    }

    private void ensureUniqueIdentifiers(final List<Long> ids, final String message) {
        final Set<Long> uniqueIds = Set.copyOf(ids);
        if (uniqueIds.size() != ids.size()) {
            throw new InvalidOperationException(message);
        }
    }

    private Map<Long, CompleteCycleCountItemRequest> mapRequestedItems(final List<CompleteCycleCountItemRequest> items) {
        final Map<Long, CompleteCycleCountItemRequest> requestedItemsById = new LinkedHashMap<>();
        for (CompleteCycleCountItemRequest item : items) {
            final CompleteCycleCountItemRequest previous = requestedItemsById.putIfAbsent(item.itemId(), item);
            if (previous != null) {
                throw new InvalidOperationException("Cycle count item ids must be unique");
            }
        }

        return requestedItemsById;
    }

    private CycleCountDTO toDto(final CycleCount cycleCount, final List<CycleCountItem> items) {
        return new CycleCountDTO(
                cycleCount.getId(),
                cycleCount.getCountDate(),
                cycleCount.getStatus(),
                cycleCount.getLocationId(),
                cycleCount.getCreatedBy(),
                cycleCount.getCompletedBy(),
                cycleCount.getCreatedAt(),
                cycleCount.getCompletedAt(),
                items.stream().map(this::toItemDto).toList());
    }

    private CycleCountItemDTO toItemDto(final CycleCountItem item) {
        return new CycleCountItemDTO(
                item.getId(),
                item.getCycleCountId(),
                item.getInventoryId(),
                nullSafeQuantity(item.getExpectedQuantity()),
                item.getActualQuantity(),
                item.getVariance(),
                item.getCountedBy(),
                item.getCountedAt(),
                item.getNotes());
    }

    private int nullSafeQuantity(final Integer quantity) {
        return quantity == null ? 0 : quantity;
    }
}
