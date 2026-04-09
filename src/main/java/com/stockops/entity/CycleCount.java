package com.stockops.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cycle count header entity.
 * Tracks the counting session lifecycle for a single location.
 *
 * @author StockOps Team
 * @since 1.0
 * @see CycleCountItem
 */
@Data
@Entity
@Table(name = "cycle_counts")
@NoArgsConstructor
public class CycleCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "count_date", nullable = false)
    private LocalDate countDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CycleCountStatus status = CycleCountStatus.PENDING;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "completed_by")
    private Long completedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }

        if (status == null) {
            status = CycleCountStatus.PENDING;
        }
    }
}
