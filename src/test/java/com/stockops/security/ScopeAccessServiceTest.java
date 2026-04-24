package com.stockops.security;

import com.stockops.dto.ScopeAssignmentRequest;
import com.stockops.entity.Center;
import com.stockops.entity.Role;
import com.stockops.entity.User;
import com.stockops.entity.Warehouse;
import com.stockops.exception.ResourceNotFoundException;
import com.stockops.repository.CenterRepository;
import com.stockops.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScopeAccessServiceTest {

    @Mock
    private CenterRepository centerRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    private ScopeAccessService scopeAccessService;

    @BeforeEach
    void setUp() {
        scopeAccessService = new ScopeAccessService(centerRepository, warehouseRepository);
    }

    @Test
    void buildUserProfileReturnsGlobalWhenNoAssignmentsExist() {
        Role role = new Role();
        User user = new User();
        user.setRole(role);

        ScopeAccessProfile profile = scopeAccessService.buildUserProfile(user);

        assertThat(profile.global()).isTrue();
        assertThat(profile.toDto().assignments()).hasSize(1);
        assertThat(profile.toDto().assignments().get(0).scope()).isEqualTo(ScopeType.GLOBAL);
    }

    @Test
    void buildUserProfileMergesRoleAndUserAssignments() {
        Role role = new Role();
        role.setScopeAssignments(Set.of(new ScopeAssignment(ScopeType.CENTER, 1L, null)));

        User user = new User();
        user.setRole(role);
        user.setScopeAssignments(Set.of(new ScopeAssignment(ScopeType.WAREHOUSE, 1L, 10L)));

        when(centerRepository.existsById(1L)).thenReturn(true);
        when(warehouseRepository.existsById(10L)).thenReturn(true);

        ScopeAccessProfile profile = scopeAccessService.buildUserProfile(user);

        assertThat(profile.global()).isFalse();
        assertThat(profile.centerIds()).containsExactly(1L);
        assertThat(profile.warehouseIds()).containsExactly(10L);
        assertThat(profile.canAccessCenter(1L)).isTrue();
        assertThat(profile.canAccessWarehouse(11L)).isFalse();
    }

    @Test
    void normalizeAssignmentsResolvesWarehouseCenterId() {
        Warehouse warehouse = new Warehouse();
        Center center = new Center();
        center.setId(1L);
        warehouse.setId(10L);
        warehouse.setCenter(center);
        when(warehouseRepository.findById(10L)).thenReturn(Optional.of(warehouse));

        Set<ScopeAssignment> assignments = scopeAccessService.normalizeAssignments(
                List.of(new ScopeAssignmentRequest(ScopeType.WAREHOUSE, null, 10L)));

        assertThat(assignments).containsExactly(new ScopeAssignment(ScopeType.WAREHOUSE, 1L, 10L));
    }

    @Test
    void normalizeAssignmentsRejectsMissingCenter() {
        when(centerRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> scopeAccessService.normalizeAssignments(
                List.of(new ScopeAssignmentRequest(ScopeType.CENTER, 99L, null))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Center not found: 99");
    }
}
