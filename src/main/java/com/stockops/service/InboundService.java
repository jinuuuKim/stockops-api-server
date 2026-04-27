package com.stockops.service;

import com.stockops.dto.AddInboundItemRequest;
import com.stockops.dto.CreateInboundRequest;
import com.stockops.dto.InboundDTO;
import com.stockops.dto.InboundItemDTO;
import com.stockops.entity.Inbound;
import com.stockops.entity.InboundItem;
import com.stockops.entity.Location;
import com.stockops.entity.Lot;
import com.stockops.entity.LotStatus;
import com.stockops.entity.Product;
import com.stockops.entity.WarehouseStatus;
import com.stockops.exception.InvalidOperationException;
import com.stockops.exception.ResourceNotFoundException;
import com.stockops.repository.InboundItemRepository;
import com.stockops.repository.InboundRepository;
import com.stockops.repository.LocationRepository;
import com.stockops.repository.LotRepository;
import com.stockops.repository.ProductRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inbound registration business logic.
 * Creates draft inbound documents, adds draft items, and confirms stock receipts into lots and inventory.
 *
 * @author StockOps Team
 * @since 1.0
 * @see InboundRepository
 * @see InboundItemRepository
 * @see InventoryService
 */
@Service
@RequiredArgsConstructor
public class InboundService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String REFERENCE_TYPE_INBOUND = "INBOUND";

    private final InboundRepository inboundRepository;
    private final InboundItemRepository inboundItemRepository;
    private final InventoryService inventoryService;
    private final LotRepository lotRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;

    /**
     * Creates a draft inbound header.
     *
     * @param request header creation payload
     * @param userId authenticated user identifier
     * @return created inbound DTO
     */
    @Transactional
    public InboundDTO createInbound(final CreateInboundRequest request, final Long userId) {
        final Inbound inbound = new Inbound();
        inbound.setInboundDate(request.inboundDate() != null ? request.inboundDate() : LocalDate.now());
        inbound.setSupplier(request.supplier());
        inbound.setStatus(STATUS_DRAFT);
        inbound.setTotalQuantity(0);
        inbound.setCreatedBy(userId);

        return toDTO(inboundRepository.save(inbound));
    }

    /**
     * Adds a draft item to an inbound header.
     *
     * @param inboundId inbound identifier
     * @param request item creation payload
     * @return created inbound item DTO
     * @throws ResourceNotFoundException when inbound, product, or location does not exist
     * @throws InvalidOperationException when the inbound is not editable
     */
    @Transactional
    public InboundItemDTO addItem(final Long inboundId, final AddInboundItemRequest request) {
        final Inbound inbound = findInboundById(inboundId);
        validateDraftStatus(inbound);

        final Product product = findProductById(request.productId());
        final Location location = findLocationById(request.locationId());
        assertLocationWarehouseNotClosed(location.getId());

        final InboundItem item = new InboundItem();
        item.setInboundId(inboundId);
        item.setProductId(product.getId());
        item.setLotNumber(request.lotNumber());
        item.setExpiryDate(request.expiryDate());
        item.setQuantity(request.quantity());
        item.setLocationId(location.getId());

        final InboundItem saved = inboundItemRepository.save(item);

        inbound.setTotalQuantity(inbound.getTotalQuantity() + request.quantity());
        inboundRepository.save(inbound);

        return toItemDTO(saved, product, location);
    }

    /**
     * Confirms a draft inbound and books stock into inventory.
     * Existing lots are reused by product and lot number; otherwise a new lot is created.
     *
     * @param inboundId inbound identifier
     * @param userId authenticated user identifier
     * @return confirmed inbound DTO
     * @throws ResourceNotFoundException when the inbound does not exist
     * @throws InvalidOperationException when the inbound cannot be confirmed
     */
    @Transactional
    public InboundDTO confirmInbound(final Long inboundId, final Long userId) {
        final Inbound inbound = findInboundById(inboundId);
        validateDraftStatus(inbound);

        final List<InboundItem> items = inboundItemRepository.findByInboundId(inboundId);
        if (items.isEmpty()) {
            throw new InvalidOperationException("Cannot confirm inbound with no items");
        }

        for (InboundItem item : items) {
            findProductById(item.getProductId());
            findLocationById(item.getLocationId());
            assertLocationWarehouseNotClosed(item.getLocationId());

            final Lot lot = lotRepository.findByLotNumberAndProductId(item.getLotNumber(), item.getProductId())
                    .map(existingLot -> updateExistingLot(existingLot, item))
                    .orElseGet(() -> createLot(item));

            inventoryService.increaseStock(
                    item.getProductId(),
                    item.getLocationId(),
                    lot.getId(),
                    item.getQuantity(),
                    REFERENCE_TYPE_INBOUND,
                    inboundId,
                    userId);

            lot.setQuantity(nullSafeQuantity(lot.getQuantity()) + item.getQuantity());
            lotRepository.save(lot);
        }

        inbound.setStatus(STATUS_CONFIRMED);
        return toDTO(inboundRepository.save(inbound));
    }

    /**
     * Returns an inbound header.
     *
     * @param id inbound identifier
     * @return inbound DTO
     */
    @Transactional(readOnly = true)
    public InboundDTO getInbound(final Long id) {
        return toDTO(findInboundById(id));
    }

    /**
     * Returns all inbounds.
     *
     * @return list of inbound DTOs
     */
    @Transactional(readOnly = true)
    public List<InboundDTO> getAllInbounds() {
        return inboundRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Returns inbounds filtered by status.
     *
     * @param status status filter
     * @return list of inbound DTOs
     */
    @Transactional(readOnly = true)
    public List<InboundDTO> getInboundsByStatus(final String status) {
        return inboundRepository.findByStatus(status).stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Returns all items for an inbound header.
     *
     * @param inboundId inbound identifier
     * @return inbound item DTOs
     */
    @Transactional(readOnly = true)
    public List<InboundItemDTO> getInboundItems(final Long inboundId) {
        if (!inboundRepository.existsById(inboundId)) {
            throw new ResourceNotFoundException("Inbound not found: " + inboundId);
        }

        return inboundItemRepository.findByInboundId(inboundId).stream()
                .map(this::toItemDTO)
                .toList();
    }

    private Inbound findInboundById(final Long inboundId) {
        return inboundRepository.findById(inboundId)
                .orElseThrow(() -> new ResourceNotFoundException("Inbound not found: " + inboundId));
    }

    private Product findProductById(final Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
    }

    private Location findLocationById(final Long locationId) {
        return locationRepository.findById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + locationId));
    }

    private void validateDraftStatus(final Inbound inbound) {
        if (!STATUS_DRAFT.equals(inbound.getStatus())) {
            throw new InvalidOperationException("Inbound must be in DRAFT status");
        }
    }

    private void assertLocationWarehouseNotClosed(final Long locationId) {
        final Location location = findLocationById(locationId);
        if (location.getWarehouse() != null && location.getWarehouse().getStatus() == WarehouseStatus.CLOSED) {
            throw new InvalidOperationException(
                    "Inbound not allowed to closed warehouse: " + location.getWarehouse().getName());
        }
    }

    private Lot createLot(final InboundItem item) {
        final Lot lot = new Lot();
        lot.setLotNumber(item.getLotNumber());
        lot.setProductId(item.getProductId());
        lot.setExpiryDate(item.getExpiryDate());
        lot.setReceivedDate(LocalDate.now());
        lot.setQuantity(0);
        lot.setStatus(LotStatus.ACTIVE);
        return lotRepository.save(lot);
    }

    private Lot updateExistingLot(final Lot lot, final InboundItem item) {
        if (lot.getExpiryDate() == null && item.getExpiryDate() != null) {
            lot.setExpiryDate(item.getExpiryDate());
        }
        if (lot.getReceivedDate() == null) {
            lot.setReceivedDate(LocalDate.now());
        }
        if (lot.getStatus() == null) {
            lot.setStatus(LotStatus.ACTIVE);
        }
        return lot;
    }

    private InboundDTO toDTO(final Inbound inbound) {
        return new InboundDTO(
                inbound.getId(),
                inbound.getInboundDate(),
                inbound.getSupplier(),
                inbound.getStatus(),
                nullSafeQuantity(inbound.getTotalQuantity()),
                inbound.getCreatedBy(),
                inbound.getCreatedAt(),
                inbound.getUpdatedAt());
    }

    private InboundItemDTO toItemDTO(final InboundItem item) {
        final Product product = findProductById(item.getProductId());
        final Location location = findLocationById(item.getLocationId());
        return toItemDTO(item, product, location);
    }

    private InboundItemDTO toItemDTO(final InboundItem item, final Product product, final Location location) {
        return new InboundItemDTO(
                item.getId(),
                item.getInboundId(),
                item.getProductId(),
                product.getName(),
                item.getLotNumber(),
                item.getExpiryDate(),
                nullSafeQuantity(item.getQuantity()),
                item.getLocationId(),
                location.getCode(),
                item.getCreatedAt());
    }

    private int nullSafeQuantity(final Integer quantity) {
        return quantity == null ? 0 : quantity;
    }
}
