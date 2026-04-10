package com.stockops.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Purchase Order Shipment Item entity.
 * Represents individual shipment line items.
 *
 * @author StockOps Team
 * @since 2.0
 */
@Data
@Entity
@Table(name = "purchase_order_shipment_items")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PurchaseOrderShipmentItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id", nullable = false)
    private PurchaseOrderShipment shipment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_item_id", nullable = false)
    private PurchaseOrderItem purchaseOrderItem;

    @Column(name = "shipped_quantity", nullable = false)
    private Integer shippedQuantity;
}
