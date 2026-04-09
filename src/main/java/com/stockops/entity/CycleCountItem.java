package com.stockops.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cycle count detail entity.
 * Stores the expected inventory balance and the final counted result for one inventory row.
 *
 * @author StockOps Team
 * @since 1.0
 * @see CycleCount
 */
@Data
@Entity
@Table(name = "cycle_count_items")
@NoArgsConstructor
public class CycleCountItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_count_id", nullable = false)
    private Long cycleCountId;

    @Column(name = "inventory_id", nullable = false)
    private Long inventoryId;

    @Column(name = "expected_quantity", nullable = false)
    private Integer expectedQuantity;

    @Column(name = "actual_quantity")
    private Integer actualQuantity;

    @Column(name = "variance")
    private Integer variance;

    @Column(name = "counted_by")
    private Long countedBy;

    @Column(name = "counted_at")
    private Instant countedAt;

    @Column(name = "notes")
    private String notes;
}
