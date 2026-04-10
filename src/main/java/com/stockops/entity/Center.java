package com.stockops.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Center entity - groups warehouses in the same location.
 * Represents a logical grouping of warehouses for inventory aggregation.
 *
 * @author StockOps Team
 * @since 2.0
 */
@Data
@Entity
@Table(name = "centers")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Center extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "address")
    private String address;

    @Column(name = "phone")
    private String phone;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";
}
