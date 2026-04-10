package com.stockops.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Purchase Order Item entity.
 * Represents individual line items in a purchase order.
 *
 * @author StockOps Team
 * @since 2.0
 */
@Data
@Entity
@Table(name = "purchase_order_items")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PurchaseOrderItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "requested_quantity", nullable = false)
    private Integer requestedQuantity;

    @Column(name = "accepted_quantity")
    private Integer acceptedQuantity = 0;

    @Column(name = "cancelled_quantity")
    private Integer cancelledQuantity = 0;

    @Column(name = "unit_price", precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_price", precision = 15, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "note")
    private String note;
}
