package com.stockops.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Inventory transfer entity.
 * Represents a request to move stock from one location to another within the same center.
 *
 * @author StockOps Team
 * @since 2.0
 * @see InventoryTransferStatus
 * @see Inventory
 */
@Data
@Entity
@Table(name = "inventory_transfers")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class InventoryTransfer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "lot_id")
    private Long lotId;

    @Column(name = "from_location_id", nullable = false)
    private Long fromLocationId;

    @Column(name = "to_location_id", nullable = false)
    private Long toLocationId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InventoryTransferStatus status = InventoryTransferStatus.REQUESTED;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "completed_by")
    private Long completedBy;

    @Column(name = "notes")
    private String notes;
}
