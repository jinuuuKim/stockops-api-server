package com.stockops.security;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import java.util.Objects;

/**
 * Embedded scope assignment used by users and roles.
 *
 * @author StockOps Team
 * @since 2.0
 */
@Embeddable
public class ScopeAssignment {

    @Convert(converter = ScopeTypeConverter.class)
    @Column(name = "scope_type", nullable = false)
    private ScopeType scope;

    @Column(name = "center_id")
    private Long centerId;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    /**
     * Creates a global scope assignment.
     *
     * @return global assignment
     */
    public static ScopeAssignment global() {
        return admin();
    }

    public static ScopeAssignment admin() {
        return new ScopeAssignment(ScopeType.ADMIN, null, null);
    }

    public ScopeType getScope() {
        return this.scope;
    }

    public void setScope(final ScopeType scope) {
        this.scope = scope;
    }

    public Long getCenterId() {
        return this.centerId;
    }

    public void setCenterId(final Long centerId) {
        this.centerId = centerId;
    }

    public Long getWarehouseId() {
        return this.warehouseId;
    }

    public void setWarehouseId(final Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public ScopeAssignment() {
    }

    public ScopeAssignment(final ScopeType scope, final Long centerId, final Long warehouseId) {
        this.scope = scope;
        this.centerId = centerId;
        this.warehouseId = warehouseId;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ScopeAssignment other)) {
            return false;
        }
        return this.scope == other.scope
                && Objects.equals(this.centerId, other.centerId)
                && Objects.equals(this.warehouseId, other.warehouseId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.scope, this.centerId, this.warehouseId);
    }
}
