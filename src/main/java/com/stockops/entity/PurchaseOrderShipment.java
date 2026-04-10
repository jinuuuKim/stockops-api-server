package com.stockops.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Purchase Order Shipment entity.
 * Represents shipment information from ERP.
 *
 * @author StockOps Team
 * @since 2.0
 */
@Data
@Entity
@Table(name = "purchase_order_shipments")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PurchaseOrderShipment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "shipment_number", nullable = false)
    private String shipmentNumber;

    @Column(name = "carrier")
    private String carrier;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ShipmentStatus status = ShipmentStatus.CREATED;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "eta_date")
    private LocalDate etaDate;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "notes")
    private String notes;

    @OneToMany(mappedBy = "shipment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseOrderShipmentItem> shipmentItems = new ArrayList<>();
}
