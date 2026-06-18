package com.stockops.security;

import com.stockops.entity.Location;
import com.stockops.exception.ResourceNotFoundException;
import com.stockops.exception.ForbiddenException;
import com.stockops.repository.LocationRepository;
import com.stockops.repository.WarehouseRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Shared scope enforcement helper for service/query-layer authorization.
 *
 * @author StockOps Team
 * @since 2.0
 */
@Component
public class ScopeGuard {

    private final LocationRepository locationRepository;
    private final WarehouseRepository warehouseRepository;

    /**
     * Asserts access to a center-scoped resource.
     *
     * @param centerId center identifier
     * @throws ForbiddenException when the current user is outside the requested scope
     */
    public void assertCenterAccess(final Long centerId) {
        if (!currentScopeProfile().canAccessCenter(centerId)) {
            throw new ForbiddenException("Access denied for center: " + centerId);
        }
    }

    public void assertAdminAccess() {
        if (!currentScopeProfile().global()) {
            throw new ForbiddenException("Access denied for admin scope");
        }
    }

    /**
     * Asserts access to a warehouse-scoped resource.
     *
     * @param warehouseId warehouse identifier
     * @throws ForbiddenException when the current user is outside the requested scope
     */
    public void assertWarehouseAccess(final Long warehouseId) {
        if (!canAccessWarehouse(warehouseId)) {
            throw new ForbiddenException("Access denied for warehouse: " + warehouseId);
        }
    }

    public void assertStoreAccess(final Long storeId) {
        assertWarehouseAccess(storeId);
    }

    /**
     * Asserts access to a location-scoped resource by resolving its warehouse and center hierarchy.
     *
     * @param locationId location identifier
     * @throws ResourceNotFoundException when the location does not exist
     * @throws ForbiddenException when the current user is outside the requested scope
     */
    public void assertLocationAccess(final Long locationId) {
        final LocationScope locationScope = loadLocationScopes(List.of(locationId)).get(locationId);
        if (locationScope == null) {
            throw new ResourceNotFoundException("Location not found: " + locationId);
        }
        if (!canAccessScope(currentScopeProfile(), locationScope.centerId(), locationScope.warehouseId())) {
            throw new ForbiddenException("Access denied for location: " + locationId);
        }
    }

    /**
     * Asserts access to a combined center/warehouse resource.
     * When a warehouse is supplied, warehouse visibility becomes authoritative and center scope can satisfy it.
     *
     * @param centerId center identifier
     * @param warehouseId warehouse identifier
     * @throws ForbiddenException when the current user is outside the requested scope
     */
    public void assertCenterWarehouseAccess(final Long centerId, final Long warehouseId) {
        if (warehouseId != null) {
            assertWarehouseAccess(warehouseId);
            return;
        }
        if (centerId != null) {
            assertCenterAccess(centerId);
            return;
        }
        if (!currentScopeProfile().global()) {
            throw new ForbiddenException("Access denied for unscoped resource");
        }
    }

    /**
     * Filters center identifiers to those visible to the current user.
     *
     * @param centerIds candidate center ids
     * @return in-scope center ids only
     */
    public List<Long> filterCenterIds(final Collection<Long> centerIds) {
        final ScopeAccessProfile profile = currentScopeProfile();
        return centerIds.stream().filter(profile::canAccessCenter).distinct().toList();
    }

    /**
     * Filters warehouse identifiers to those visible to the current user.
     *
     * @param warehouseIds candidate warehouse ids
     * @return in-scope warehouse ids only
     */
    public List<Long> filterWarehouseIds(final Collection<Long> warehouseIds) {
        return warehouseIds.stream().filter(this::canAccessWarehouse).distinct().toList();
    }

    /**
     * Returns whether the current user can access the supplied warehouse either directly or via its parent center.
     *
     * @param warehouseId warehouse identifier
     * @return {@code true} when the warehouse is visible
     */
    public boolean canAccessWarehouse(final Long warehouseId) {
        if (warehouseId == null) {
            return true;
        }

        final ScopeAccessProfile profile = currentScopeProfile();
        if (profile.global() || profile.warehouseIds().contains(warehouseId)) {
            return true;
        }

        return warehouseRepository.findById(warehouseId)
                .map(warehouse -> warehouse.getCenter() != null && profile.centerIds().contains(warehouse.getCenter().getId()))
                .orElse(false);
    }

    /**
     * Returns whether the current user can access the supplied location.
     *
     * @param locationId location identifier
     * @return {@code true} when the location is visible
     */
    public boolean canAccessLocation(final Long locationId) {
        final LocationScope scope = loadLocationScopes(List.of(locationId)).get(locationId);
        return scope != null && canAccessScope(currentScopeProfile(), scope.centerId(), scope.warehouseId());
    }

    /**
     * Filters rows whose scope is derived from a location identifier.
     *
     * @param rows candidate rows
     * @param locationIdExtractor row-to-location mapper
     * @param <T> row type
     * @return in-scope rows only
     */
    public <T> List<T> filterByLocationScope(final Collection<T> rows,
                                             final Function<T, Long> locationIdExtractor) {
        final ScopeAccessProfile profile = currentScopeProfile();
        if (profile.global()) {
            return List.copyOf(rows);
        }

        final List<Long> locationIds = rows.stream()
                .map(locationIdExtractor)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        final Map<Long, LocationScope> scopes = loadLocationScopes(locationIds);

        return rows.stream()
                .filter(row -> {
                    final Long locationId = locationIdExtractor.apply(row);
                    final LocationScope scope = scopes.get(locationId);
                    return scope != null && canAccessScope(profile, scope.centerId(), scope.warehouseId());
                })
                .toList();
    }

    /**
     * Filters rows whose scope is already expressed as center/warehouse identifiers.
     *
     * @param rows candidate rows
     * @param centerIdExtractor row-to-center mapper
     * @param warehouseIdExtractor row-to-warehouse mapper
     * @param <T> row type
     * @return in-scope rows only
     */
    public <T> List<T> filterByCenterWarehouseScope(final Collection<T> rows,
                                                    final Function<T, Long> centerIdExtractor,
                                                    final Function<T, Long> warehouseIdExtractor) {
        final ScopeAccessProfile profile = currentScopeProfile();
        if (profile.global()) {
            return List.copyOf(rows);
        }

        return rows.stream()
                .filter(row -> canAccessScope(profile, centerIdExtractor.apply(row), warehouseIdExtractor.apply(row)))
                .toList();
    }

    private ScopeAccessProfile currentScopeProfile() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof ScopedUserDetails userDetails)) {
            throw new ForbiddenException("Authentication required for scoped access");
        }
        return userDetails.getScopeAccessProfile();
    }

    private Map<Long, LocationScope> loadLocationScopes(final Collection<Long> locationIds) {
        if (locationIds.isEmpty()) {
            return Map.of();
        }

        final Map<Long, LocationScope> scopes = new HashMap<>();
        for (Location location : locationRepository.findByIdInWithWarehouseAndCenter(locationIds)) {
            final Long warehouseId = location.getWarehouse() == null ? null : location.getWarehouse().getId();
            final Long centerId = location.getWarehouse() == null || location.getWarehouse().getCenter() == null
                    ? null
                    : location.getWarehouse().getCenter().getId();
            scopes.put(location.getId(), new LocationScope(centerId, warehouseId));
        }
        return scopes;
    }

    private boolean canAccessScope(final ScopeAccessProfile profile, final Long centerId, final Long warehouseId) {
        if (profile.global()) {
            return true;
        }
        if (warehouseId != null && profile.warehouseIds().contains(warehouseId)) {
            return true;
        }
        return centerId != null && profile.centerIds().contains(centerId);
    }

    private record LocationScope(Long centerId, Long warehouseId) {
    }

    public ScopeGuard(final LocationRepository locationRepository, final WarehouseRepository warehouseRepository) {
        this.locationRepository = locationRepository;
        this.warehouseRepository = warehouseRepository;
    }
}
