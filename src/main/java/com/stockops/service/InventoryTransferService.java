package com.stockops.service;

import com.stockops.dto.CreateInventoryTransferRequest;
import com.stockops.dto.InventoryTransferDTO;
import com.stockops.entity.InventoryTransfer;
import com.stockops.entity.InventoryTransferStatus;
import com.stockops.entity.Location;
import com.stockops.entity.User;
import com.stockops.entity.WarehouseStatus;
import com.stockops.exception.InvalidOperationException;
import com.stockops.exception.ResourceNotFoundException;
import com.stockops.repository.InventoryTransferRepository;
import com.stockops.repository.LocationRepository;
import com.stockops.repository.UserRepository;
import com.stockops.repository.WarehouseRepository;
import com.stockops.security.ScopeGuard;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for inventory transfer management.
 * Handles creation, completion, and cancellation of stock transfers between locations.
 * Transfer completion atomically deducts from source and adds to destination inventory.
 *
 * @author StockOps Team
 * @since 2.0
 * @see InventoryTransferRepository
 * @see InventoryService
 */
@Service
@RequiredArgsConstructor
public class InventoryTransferService {

    private final InventoryTransferRepository transferRepository;
    private final InventoryService inventoryService;
    private final LocationRepository locationRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final ScopeGuard scopeGuard;

    /**
     * Creates a new inventory transfer request.
     *
     * @param request transfer creation payload
     * @param userId requesting operator identifier
     * @return created transfer response
     * @throws ResourceNotFoundException when a location does not exist
     * @throws InvalidOperationException when locations are in different centers or quantity is invalid
     */
    @Transactional
    public InventoryTransferDTO createTransfer(final CreateInventoryTransferRequest request, final Long userId) {
        validateLocationsSameCenter(request.fromLocationId(), request.toLocationId());
        scopeGuard.assertLocationAccess(request.fromLocationId());
        scopeGuard.assertLocationAccess(request.toLocationId());

        if (request.quantity() <= 0) {
            throw new InvalidOperationException("Quantity must be greater than zero");
        }

        final InventoryTransfer transfer = new InventoryTransfer();
        transfer.setProductId(request.productId());
        transfer.setLotId(request.lotId());
        transfer.setFromLocationId(request.fromLocationId());
        transfer.setToLocationId(request.toLocationId());
        transfer.setQuantity(request.quantity());
        transfer.setStatus(InventoryTransferStatus.REQUESTED);
        transfer.setRequestedBy(userId);
        transfer.setNotes(request.notes());

        return toDto(transferRepository.save(transfer));
    }

    /**
     * Retrieves all inventory transfers visible to the current user.
     *
     * @return list of transfer responses
     */
    @Transactional(readOnly = true)
    public List<InventoryTransferDTO> findAll() {
        return filterScopedTransfers(transferRepository.findAll());
    }

    /**
     * Retrieves an inventory transfer by identifier.
     *
     * @param id transfer identifier
     * @return transfer response
     * @throws ResourceNotFoundException when the transfer does not exist
     */
    @Transactional(readOnly = true)
    public InventoryTransferDTO findById(final Long id) {
        final InventoryTransfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found: " + id));
        assertTransferAccess(transfer);
        return toDto(transfer);
    }

    /**
     * Completes a requested transfer by atomically moving stock.
     * Deducts from source inventory and adds to destination inventory.
     *
     * @param id transfer identifier
     * @param userId completing operator identifier
     * @return completed transfer response
     * @throws ResourceNotFoundException when the transfer does not exist
     * @throws InvalidOperationException when the transfer is not in REQUESTED status
     */
    @Transactional
    public InventoryTransferDTO completeTransfer(final Long id, final Long userId) {
        final InventoryTransfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found: " + id));
        assertTransferAccess(transfer);

        if (transfer.getStatus() != InventoryTransferStatus.REQUESTED) {
            throw new InvalidOperationException("Only REQUESTED transfers can be completed");
        }

        final Location fromLocation = locationRepository.findById(transfer.getFromLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Source location not found: " + transfer.getFromLocationId()));
        final Location toLocation = locationRepository.findById(transfer.getToLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination location not found: " + transfer.getToLocationId()));
        assertLocationWarehouseNotClosed(fromLocation);
        assertLocationWarehouseNotClosed(toLocation);

        inventoryService.decreaseStock(
                transfer.getProductId(),
                transfer.getFromLocationId(),
                transfer.getLotId(),
                transfer.getQuantity(),
                "TRANSFER",
                transfer.getId(),
                userId
        );

        inventoryService.increaseStock(
                transfer.getProductId(),
                transfer.getToLocationId(),
                transfer.getLotId(),
                transfer.getQuantity(),
                "TRANSFER",
                transfer.getId(),
                userId
        );

        transfer.setStatus(InventoryTransferStatus.COMPLETED);
        transfer.setCompletedBy(userId);

        return toDto(transferRepository.save(transfer));
    }

    /**
     * Cancels a requested transfer without moving stock.
     *
     * @param id transfer identifier
     * @param userId cancelling operator identifier
     * @return cancelled transfer response
     * @throws ResourceNotFoundException when the transfer does not exist
     * @throws InvalidOperationException when the transfer is not in REQUESTED status
     */
    @Transactional
    public InventoryTransferDTO cancelTransfer(final Long id, final Long userId) {
        final InventoryTransfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found: " + id));
        assertTransferAccess(transfer);

        if (transfer.getStatus() != InventoryTransferStatus.REQUESTED) {
            throw new InvalidOperationException("Only REQUESTED transfers can be cancelled");
        }

        transfer.setStatus(InventoryTransferStatus.CANCELLED);
        transfer.setCompletedBy(userId);

        return toDto(transferRepository.save(transfer));
    }

    private void validateLocationsSameCenter(final Long fromLocationId, final Long toLocationId) {
        if (fromLocationId.equals(toLocationId)) {
            throw new InvalidOperationException("Source and destination locations must be different");
        }

        final Location fromLocation = locationRepository.findById(fromLocationId)
                .orElseThrow(() -> new ResourceNotFoundException("Source location not found: " + fromLocationId));
        final Location toLocation = locationRepository.findById(toLocationId)
                .orElseThrow(() -> new ResourceNotFoundException("Destination location not found: " + toLocationId));

        assertLocationWarehouseNotClosed(fromLocation);
        assertLocationWarehouseNotClosed(toLocation);

        final Long fromCenterId = resolveCenterId(fromLocation);
        final Long toCenterId = resolveCenterId(toLocation);

        if (fromCenterId == null || toCenterId == null || !fromCenterId.equals(toCenterId)) {
            throw new InvalidOperationException("Transfers are only allowed between locations in the same center");
        }
    }

    private void assertLocationWarehouseNotClosed(final Location location) {
        if (location.getWarehouse() != null && location.getWarehouse().getStatus() == WarehouseStatus.CLOSED) {
            throw new InvalidOperationException(
                    "Transfer not allowed for closed warehouse: " + location.getWarehouse().getName());
        }
    }

    private Long resolveCenterId(final Location location) {
        if (location.getWarehouse() != null && location.getWarehouse().getCenter() != null) {
            return location.getWarehouse().getCenter().getId();
        }
        return null;
    }

    private void assertTransferAccess(final InventoryTransfer transfer) {
        scopeGuard.assertLocationAccess(transfer.getFromLocationId());
        scopeGuard.assertLocationAccess(transfer.getToLocationId());
    }

    private List<InventoryTransferDTO> filterScopedTransfers(final List<InventoryTransfer> transfers) {
        return transfers.stream()
                .filter(transfer -> scopeGuard.canAccessLocation(transfer.getFromLocationId())
                        && scopeGuard.canAccessLocation(transfer.getToLocationId()))
                .map(this::toDto)
                .toList();
    }

    private InventoryTransferDTO toDto(final InventoryTransfer transfer) {
        return new InventoryTransferDTO(
                transfer.getId(),
                transfer.getProductId(),
                transfer.getLotId(),
                transfer.getFromLocationId(),
                transfer.getToLocationId(),
                transfer.getQuantity(),
                transfer.getStatus(),
                transfer.getRequestedBy(),
                findUserName(transfer.getRequestedBy()),
                transfer.getCompletedBy(),
                findUserName(transfer.getCompletedBy()),
                transfer.getNotes(),
                transfer.getCreatedAt(),
                transfer.getUpdatedAt()
        );
    }

    private String findUserName(final Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .map(User::getName)
                .orElse(null);
    }
}
