package com.stockops.notification.lowstock;

import com.stockops.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Per-scope throttle state for low-stock webhook notifications.
 *
 * <p>{@code scopeKey} is {@code "WAREHOUSE:<id>"} for a warehouse card or {@code "GLOBAL"} for the
 * system-wide admin escalation. {@code lastNotifiedAt} is an unconditional cooldown gate: a scope is
 * re-notified only after the configured cooldown has elapsed, regardless of stock recovering and
 * dipping again in between (that flapping must not produce repeat cards).
 *
 * @author StockOps Team
 * @since 2.5
 */
@Entity
@Table(name = "low_stock_alert_state")
public class LowStockAlertState extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scope_key", nullable = false, unique = true, length = 100)
    private String scopeKey;

    @Column(name = "last_notified_at", nullable = false)
    private Instant lastNotifiedAt;

    @Column(name = "last_low_count", nullable = false)
    private int lastLowCount;

    public LowStockAlertState() {
    }

    public LowStockAlertState(final String scopeKey, final Instant lastNotifiedAt, final int lastLowCount) {
        this.scopeKey = scopeKey;
        this.lastNotifiedAt = lastNotifiedAt;
        this.lastLowCount = lastLowCount;
    }

    public Long getId() {
        return this.id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public String getScopeKey() {
        return this.scopeKey;
    }

    public void setScopeKey(final String scopeKey) {
        this.scopeKey = scopeKey;
    }

    public Instant getLastNotifiedAt() {
        return this.lastNotifiedAt;
    }

    public void setLastNotifiedAt(final Instant lastNotifiedAt) {
        this.lastNotifiedAt = lastNotifiedAt;
    }

    public int getLastLowCount() {
        return this.lastLowCount;
    }

    public void setLastLowCount(final int lastLowCount) {
        this.lastLowCount = lastLowCount;
    }
}
