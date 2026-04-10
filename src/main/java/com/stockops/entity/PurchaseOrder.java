package com.stockops.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Purchase Order header entity.
 * Represents a purchase order request from a center to ERP.
 *
 * @author StockOps Team
 * @since 2.0
 */
@Data
@Entity
@Table(name = "purchase_orders")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PurchaseOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "po_number", nullable = false, unique = true)
    private String poNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requesting_center_id", nullable = false)
    private Center requestingCenter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_warehouse_id")
    private Warehouse targetWarehouse;

    @Column(name = "supplier_name")
    private String supplierName;

    @Column(name = "supplier_code")
    private String supplierCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PurchaseOrderStatus status = PurchaseOrderStatus.DRAFT;

    @Column(name = "erp_reference")
    private String erpReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by")
    private User requestedBy;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "erp_responded_at")
    private LocalDateTime erpRespondedAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "total_requested_amount", precision = 15, scale = 2)
    private BigDecimal totalRequestedAmount = BigDecimal.ZERO;

    @Column(name = "total_accepted_amount", precision = 15, scale = 2)
    private BigDecimal totalAcceptedAmount = BigDecimal.ZERO;

    @Column(name = "notes")
    private String notes;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseOrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseOrderShipment> shipments = new ArrayList<>();
}
