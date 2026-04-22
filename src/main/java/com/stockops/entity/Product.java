package com.stockops.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * Product master entity.
 * Soft-deleted products remain stored for audit/history while staying hidden from active queries.
 *
 * @author StockOps Team
 * @since 1.0
 */
@Getter
@Setter
@Entity
@Table(name = "products")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SQLRestriction("deleted = false")
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(name = "barcode", nullable = false)
    private String barcode;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "category")
    private String category;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "expiry_managed", nullable = false)
    private boolean expiryManaged;

    @Column(name = "default_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal defaultPrice = BigDecimal.ZERO;

    @Column(name = "safety_stock_quantity", nullable = false)
    private Integer safetyStockQuantity = 0;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
