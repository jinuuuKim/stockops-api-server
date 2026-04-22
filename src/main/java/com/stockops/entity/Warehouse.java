package com.stockops.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Warehouse entity - physical building that belongs to a center.
 * Actual inbound/outbound operations happen here.
 *
 * @author StockOps Team
 * @since 2.0
 */
@Data
@Entity
@Table(name = "warehouses", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"center_id", "code"})
})
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Warehouse extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "center_id", nullable = false)
    private Center center;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "address")
    private String address;

    @Column(name = "phone")
    private String phone;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private WarehouseStatus status = WarehouseStatus.ACTIVE;
}
