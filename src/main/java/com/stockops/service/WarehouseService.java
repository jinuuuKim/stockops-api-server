package com.stockops.service;

import com.stockops.dto.WarehouseCanCloseResponse;
import com.stockops.entity.Center;
import com.stockops.entity.Inventory;
import com.stockops.entity.InventoryTransfer;
import com.stockops.entity.InventoryTransferStatus;
import com.stockops.entity.Location;
import com.stockops.entity.Inbound;
import com.stockops.entity.InboundItem;
import com.stockops.entity.Warehouse;
import com.stockops.entity.WarehouseStatus;
import com.stockops.exception.InvalidOperationException;
import com.stockops.exception.ResourceNotFoundException;
import com.stockops.repository.InventoryRepository;
import com.stockops.repository.InventoryTransferRepository;
import com.stockops.repository.LocationRepository;
import com.stockops.repository.InboundRepository;
import com.stockops.repository.InboundItemRepository;
import com.stockops.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Service for Warehouse management.
 *
 * @author StockOps Team
 * @since 2.0
 */
@Service
@RequiredArgsConstructor
@Transactional
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final CenterService centerService;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final InboundRepository inboundRepository;
    private final InboundItemRepository inboundItemRepository;
    private final InventoryTransferRepository inventoryTransferRepository;

    public List<Warehouse> findAll() {
        return warehouseRepository.findAllWithCenter();
    }

    public List<Warehouse> findByCenterId(Long centerId) {
        return warehouseRepository.findByCenterId(centerId);
    }

    public List<Warehouse> findActiveByCenterId(Long centerId) {
        return warehouseRepository.findActiveByCenterId(centerId);
    }

    public Warehouse findById(Long id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found: " + id));
    }

    public Warehouse create(Long centerId, Warehouse warehouse) {
        Center center = centerService.findById(centerId);

        if (warehouseRepository.existsByCenterIdAndCode(centerId, warehouse.getCode())) {
            throw new InvalidOperationException("Warehouse code already exists in this center: " + warehouse.getCode());
        }

        warehouse.setCenter(center);
        warehouse.setStatus(WarehouseStatus.ACTIVE);
        return warehouseRepository.save(warehouse);
    }

    public Warehouse update(Long id, Warehouse warehouse) {
        Warehouse existing = findById(id);
        existing.setName(warehouse.getName());
        existing.setAddress(warehouse.getAddress());
        existing.setPhone(warehouse.getPhone());
        existing.setStatus(warehouse.getStatus());
        return warehouseRepository.save(existing);
    }

    public void delete(Long id) {
        Warehouse warehouse = findById(id);
        warehouse.setStatus(WarehouseStatus.CLOSED);
        warehouseRepository.save(warehouse);
    }

    /**
     * Checks whether a warehouse can be closed.
     * Validates that no remaining inventory, open inbound drafts, or open transfers exist
     * for locations within the warehouse.
     *
     * @param warehouseId warehouse identifier
     * @return true if the warehouse can be closed
     */
    @Transactional(readOnly = true)
    public boolean canClose(Long warehouseId) {
        Warehouse warehouse = findById(warehouseId);
        if (warehouse.getStatus() == WarehouseStatus.CLOSED) {
            return false;
        }

        List<Long> locationIds = locationRepository.findByWarehouseId(warehouseId)
                .stream()
                .map(Location::getId)
                .toList();

        if (locationIds.isEmpty()) {
            return true;
        }

        List<Inventory> inventories = inventoryRepository.findAllByLocationIdIn(locationIds);
        boolean hasInventory = inventories.stream()
                .anyMatch(inv -> nullSafeQuantity(inv.getQuantity()) > 0);
        if (hasInventory) {
            return false;
        }

        List<Inbound> draftInbounds = inboundRepository.findByStatus("DRAFT");
        for (Inbound inbound : draftInbounds) {
            List<InboundItem> items = inboundItemRepository.findByInboundId(inbound.getId());
            boolean targetsWarehouse = items.stream()
                    .anyMatch(item -> locationIds.contains(item.getLocationId()));
            if (targetsWarehouse) {
                return false;
            }
        }

        for (Long locationId : locationIds) {
            List<InventoryTransfer> fromTransfers = inventoryTransferRepository.findByFromLocationId(locationId);
            List<InventoryTransfer> toTransfers = inventoryTransferRepository.findByToLocationId(locationId);
            boolean hasOpenTransfers = Stream.concat(fromTransfers.stream(), toTransfers.stream())
                    .anyMatch(t -> t.getStatus() == InventoryTransferStatus.REQUESTED);
            if (hasOpenTransfers) {
                return false;
            }
        }

        return true;
    }

    /**
     * Closes a warehouse after validating preconditions.
     * Sets status to CLOSED and records the closure reason and timestamp.
     *
     * @param warehouseId warehouse identifier
     * @param reason closure reason
     * @return closed warehouse
     * @throws ResourceNotFoundException when warehouse does not exist
     * @throws InvalidOperationException when warehouse cannot be closed
     */
    public Warehouse close(Long warehouseId, String reason) {
        if (!canClose(warehouseId)) {
            throw new InvalidOperationException(
                    "Warehouse cannot be closed. Ensure no inventory, open inbounds, or open transfers remain.");
        }
        Warehouse warehouse = findById(warehouseId);
        warehouse.setStatus(WarehouseStatus.CLOSED);
        warehouse.setClosureReason(reason);
        warehouse.setClosedAt(Instant.now());
        return warehouseRepository.save(warehouse);
    }

    /**
     * Returns detailed closure preconditions for a warehouse.
     *
     * @param warehouseId warehouse identifier
     * @return response with canClose flag and specific blocking reasons
     */
    @Transactional(readOnly = true)
    public WarehouseCanCloseResponse getCanCloseResponse(Long warehouseId) {
        Warehouse warehouse = findById(warehouseId);
        if (warehouse.getStatus() == WarehouseStatus.CLOSED) {
            List<String> reasons = List.of("이미 폐쇄된 창고입니다.");
            return new WarehouseCanCloseResponse(false, reasons, 0, 0, 0);
        }

        List<Long> locationIds = locationRepository.findByWarehouseId(warehouseId)
                .stream()
                .map(Location::getId)
                .toList();

        int remainingInventory = 0;
        int openInbounds = 0;
        int openTransfers = 0;
        List<String> reasons = new ArrayList<>();

        if (!locationIds.isEmpty()) {
            List<Inventory> inventories = inventoryRepository.findAllByLocationIdIn(locationIds);
            remainingInventory = (int) inventories.stream()
                    .filter(inv -> nullSafeQuantity(inv.getQuantity()) > 0)
                    .count();
            if (remainingInventory > 0) {
                reasons.add("남은 재고: " + remainingInventory + "개");
            }

            List<Inbound> draftInbounds = inboundRepository.findByStatus("DRAFT");
            for (Inbound inbound : draftInbounds) {
                List<InboundItem> items = inboundItemRepository.findByInboundId(inbound.getId());
                boolean targetsWarehouse = items.stream()
                        .anyMatch(item -> locationIds.contains(item.getLocationId()));
                if (targetsWarehouse) {
                    openInbounds++;
                }
            }
            if (openInbounds > 0) {
                reasons.add("진행 중 입고: " + openInbounds + "건");
            }

            for (Long locationId : locationIds) {
                List<InventoryTransfer> fromTransfers = inventoryTransferRepository.findByFromLocationId(locationId);
                List<InventoryTransfer> toTransfers = inventoryTransferRepository.findByToLocationId(locationId);
                long count = Stream.concat(fromTransfers.stream(), toTransfers.stream())
                        .filter(t -> t.getStatus() == InventoryTransferStatus.REQUESTED)
                        .count();
                openTransfers += (int) count;
            }
            if (openTransfers > 0) {
                reasons.add("진행 중 이동: " + openTransfers + "건");
            }
        }

        boolean canClose = reasons.isEmpty();
        return new WarehouseCanCloseResponse(canClose, reasons, remainingInventory, openInbounds, openTransfers);
    }

    private int nullSafeQuantity(Integer quantity) {
        return quantity == null ? 0 : quantity;
    }
}
