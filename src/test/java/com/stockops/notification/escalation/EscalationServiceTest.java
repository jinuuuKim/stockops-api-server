package com.stockops.notification.escalation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.stockops.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EscalationServiceTest {

    @Mock
    private EscalationPolicyRepository policyRepository;

    @InjectMocks
    private EscalationService escalationService;

    @Test
    void resolvePolicyReturnsWarehousePolicyWhenAvailable() {
        final EscalationPolicy warehousePolicy = new EscalationPolicy();
        warehousePolicy.setId(1L);
        warehousePolicy.setWarehouseId(10L);

        when(policyRepository.findByCenterIdAndWarehouseIdAndAlertTypeAndActiveTrue(1L, 10L, "TEMPERATURE"))
                .thenReturn(Optional.of(warehousePolicy));

        final Optional<EscalationPolicy> result = escalationService.resolvePolicy(1L, 10L, "TEMPERATURE");

        assertThat(result).isPresent();
        assertThat(result.get().getWarehouseId()).isEqualTo(10L);
    }

    @Test
    void resolvePolicyFallsBackToCenterPolicy() {
        when(policyRepository.findByCenterIdAndWarehouseIdAndAlertTypeAndActiveTrue(1L, 10L, "TEMPERATURE"))
                .thenReturn(Optional.empty());

        final EscalationPolicy centerPolicy = new EscalationPolicy();
        centerPolicy.setId(2L);
        centerPolicy.setWarehouseId(null);

        when(policyRepository.findByCenterIdAndWarehouseIdIsNullAndAlertTypeAndActiveTrue(1L, "TEMPERATURE"))
                .thenReturn(Optional.of(centerPolicy));

        final Optional<EscalationPolicy> result = escalationService.resolvePolicy(1L, 10L, "TEMPERATURE");

        assertThat(result).isPresent();
        assertThat(result.get().getWarehouseId()).isNull();
    }

    @Test
    void resolvePolicyReturnsEmptyWhenNoMatch() {
        when(policyRepository.findByCenterIdAndWarehouseIdAndAlertTypeAndActiveTrue(1L, 10L, "TEMPERATURE"))
                .thenReturn(Optional.empty());
        when(policyRepository.findByCenterIdAndWarehouseIdIsNullAndAlertTypeAndActiveTrue(1L, "TEMPERATURE"))
                .thenReturn(Optional.empty());

        final Optional<EscalationPolicy> result = escalationService.resolvePolicy(1L, 10L, "TEMPERATURE");

        assertThat(result).isEmpty();
    }

    @Test
    void getEscalationRulesThrowsWhenPolicyNotFound() {
        when(policyRepository.findByIdWithRules(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> escalationService.getEscalationRules(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getEscalationRulesReturnsOrderedRules() {
        final EscalationPolicy policy = new EscalationPolicy();
        policy.setId(1L);

        final EscalationRule rule1 = new EscalationRule();
        rule1.setLevel(2);
        final EscalationRule rule2 = new EscalationRule();
        rule2.setLevel(1);

        policy.setRules(List.of(rule1, rule2));

        when(policyRepository.findByIdWithRules(1L)).thenReturn(Optional.of(policy));

        final List<EscalationRule> result = escalationService.getEscalationRules(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getLevel()).isEqualTo(1);
        assertThat(result.get(1).getLevel()).isEqualTo(2);
    }
}
