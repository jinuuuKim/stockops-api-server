package com.stockops.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.stockops.entity.Center;
import com.stockops.entity.Location;
import com.stockops.entity.Warehouse;
import com.stockops.repository.LocationRepository;
import com.stockops.repository.WarehouseRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class ScopeGuardTest {

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    private ScopeGuard scopeGuard;

    @BeforeEach
    void setUp() {
        scopeGuard = new ScopeGuard(locationRepository, warehouseRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void centerScopedUserCanAccessWarehousesAndLocationsWithinAssignedCenter() {
        authenticate(new ScopeAccessProfile(false, List.of(new ScopeAssignment(ScopeType.CENTER, 1L, null)), java.util.Set.of(1L), java.util.Set.of()));

        final Warehouse warehouseOne = warehouse(10L, 1L);
        final Warehouse warehouseTwo = warehouse(20L, 2L);
        final Location locationOne = location(100L, warehouseOne);
        final Location locationTwo = location(200L, warehouseTwo);

        when(warehouseRepository.findById(10L)).thenReturn(java.util.Optional.of(warehouseOne));
        when(warehouseRepository.findById(20L)).thenReturn(java.util.Optional.of(warehouseTwo));
        when(locationRepository.findByIdInWithWarehouseAndCenter(any())).thenReturn(List.of(locationOne, locationTwo));

        assertThat(scopeGuard.canAccessWarehouse(10L)).isTrue();
        assertThat(scopeGuard.canAccessWarehouse(20L)).isFalse();
        assertThat(scopeGuard.canAccessLocation(100L)).isTrue();
        assertThat(scopeGuard.canAccessLocation(200L)).isFalse();
        assertThat(scopeGuard.filterByLocationScope(List.of(100L, 200L), java.util.function.Function.identity()))
                .containsExactly(100L);
    }

    private void authenticate(final ScopeAccessProfile profile) {
        final ScopedUserDetails principal = new ScopedUserDetails(
                99L,
                "scope@test.local",
                "password",
                true,
                List.of(),
                profile);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "password", principal.getAuthorities()));
    }

    private Warehouse warehouse(final Long warehouseId, final Long centerId) {
        final Center center = new Center();
        center.setId(centerId);

        final Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        warehouse.setCenter(center);
        return warehouse;
    }

    private Location location(final Long locationId, final Warehouse warehouse) {
        final Location location = new Location();
        location.setId(locationId);
        location.setWarehouse(warehouse);
        return location;
    }
}
