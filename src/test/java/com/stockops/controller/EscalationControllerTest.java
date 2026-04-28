package com.stockops.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockops.notification.escalation.EscalationPolicy;
import com.stockops.notification.escalation.EscalationPolicyRepository;
import com.stockops.notification.escalation.EscalationRule;
import com.stockops.security.PermissionChecker;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for {@link com.stockops.notification.escalation.EscalationController} REST endpoints.
 * Covers policy CRUD, resolve, and error scenarios with H2 in-memory database.
 *
 * @author StockOps Team
 * @since 2.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class EscalationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EscalationPolicyRepository policyRepository;

    @MockBean
    private PermissionChecker permissionChecker;

    private void stubPermissions() {
        when(permissionChecker.hasPermission(anyString())).thenReturn(true);
        when(permissionChecker.hasAnyPermission(any())).thenReturn(true);
        when(permissionChecker.hasCenterScope(anyLong())).thenReturn(true);
        when(permissionChecker.hasWarehouseScope(anyLong())).thenReturn(true);
        when(permissionChecker.hasPermissionForCenter(anyString(), anyLong())).thenReturn(true);
        when(permissionChecker.hasPermissionForWarehouse(anyString(), anyLong())).thenReturn(true);
    }

    private EscalationPolicy seedPolicy() {
        EscalationPolicy policy = new EscalationPolicy();
        policy.setCenterId(1L);
        policy.setAlertType("TEMPERATURE");
        policy.setActive(true);

        EscalationRule rule = new EscalationRule();
        rule.setLevel(1);
        rule.setDelayMinutes(0);
        rule.setChannels(List.of("EMAIL"));
        rule.setNotifyRoles(List.of("ADMIN"));
        rule.setPolicy(policy);
        policy.setRules(List.of(rule));

        return policyRepository.save(policy);
    }

    /**
     * Verifies that listing policies returns HTTP 200 with a JSON array.
     */
    @Test
    void listPoliciesReturns200() throws Exception {
        stubPermissions();
        seedPolicy();

        mockMvc.perform(get("/api/v1/escalation-policies?centerId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));
    }

    /**
     * Verifies that retrieving a policy by ID returns HTTP 200 with correct alert type.
     */
    @Test
    void getPolicyByIdReturns200() throws Exception {
        stubPermissions();
        EscalationPolicy policy = seedPolicy();

        mockMvc.perform(get("/api/v1/escalation-policies/{id}", policy.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertType").value("TEMPERATURE"))
                .andExpect(jsonPath("$.active").value(true));
    }

    /**
     * Verifies that resolving a policy returns HTTP 200 when a matching policy exists.
     */
    @Test
    void resolvePolicyReturns200() throws Exception {
        stubPermissions();
        seedPolicy();

        mockMvc.perform(get("/api/v1/escalation-policies/resolve?centerId=1&alertType=TEMPERATURE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertType").value("TEMPERATURE"));
    }

    /**
     * Verifies that resolving a non-existent policy returns HTTP 404.
     */
    @Test
    void resolvePolicyReturns404() throws Exception {
        stubPermissions();

        mockMvc.perform(get("/api/v1/escalation-policies/resolve?centerId=999&alertType=UNKNOWN"))
                .andExpect(status().isNotFound());
    }

    /**
     * Verifies that creating a policy returns HTTP 200 with persisted data.
     */
    @Test
    void createPolicyReturns200() throws Exception {
        stubPermissions();
        String json = """
                {"centerId":1,"warehouseId":null,"alertType":"HUMIDITY","active":true,"rules":[{"level":1,"delayMinutes":5,"notifyRoles":["ADMIN"],"channels":["EMAIL"]}]}
                """;

        mockMvc.perform(post("/api/v1/escalation-policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertType").value("HUMIDITY"))
                .andExpect(jsonPath("$.rules").isArray());
    }

    /**
     * Verifies that updating a policy returns HTTP 200 with updated data.
     */
    @Test
    void updatePolicyReturns200() throws Exception {
        stubPermissions();
        EscalationPolicy policy = seedPolicy();
        String json = """
                {"centerId":1,"warehouseId":null,"alertType":"UPDATED_TYPE","active":false,"rules":[]}
                """;

        mockMvc.perform(put("/api/v1/escalation-policies/{id}", policy.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertType").value("UPDATED_TYPE"))
                .andExpect(jsonPath("$.active").value(false));
    }

    /**
     * Verifies that deleting a policy returns HTTP 204 and soft-deletes the record.
     */
    @Test
    void deletePolicyReturns204() throws Exception {
        stubPermissions();
        EscalationPolicy policy = seedPolicy();
        Long id = policy.getId();

        mockMvc.perform(delete("/api/v1/escalation-policies/{id}", id))
                .andExpect(status().isNoContent());

        assertThat(policyRepository.findById(id))
                .isPresent()
                .hasValueSatisfying(p -> assertThat(p.isActive()).isFalse());
    }
}
