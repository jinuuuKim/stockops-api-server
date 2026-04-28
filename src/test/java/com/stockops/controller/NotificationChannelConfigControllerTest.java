package com.stockops.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockops.notification.config.NotificationChannelConfig;
import com.stockops.notification.config.NotificationChannelConfigRepository;
import com.stockops.notification.webhook.WebhookEndpointConfig;
import com.stockops.notification.webhook.WebhookEndpointConfigRepository;
import com.stockops.notification.webhook.WebhookService;
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
 * Integration tests for {@link com.stockops.notification.config.NotificationChannelConfigController} REST endpoints.
 * Covers config CRUD, resolve, and webhook testing with H2 in-memory database.
 *
 * @author StockOps Team
 * @since 2.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class NotificationChannelConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationChannelConfigRepository configRepository;

    @Autowired
    private WebhookEndpointConfigRepository webhookEndpointConfigRepository;

    @MockBean
    private PermissionChecker permissionChecker;

    @MockBean
    private WebhookService webhookService;

    private void stubPermissions() {
        when(permissionChecker.hasPermission(anyString())).thenReturn(true);
        when(permissionChecker.hasAnyPermission(any())).thenReturn(true);
        when(permissionChecker.hasCenterScope(anyLong())).thenReturn(true);
        when(permissionChecker.hasWarehouseScope(anyLong())).thenReturn(true);
        when(permissionChecker.hasPermissionForCenter(anyString(), anyLong())).thenReturn(true);
        when(permissionChecker.hasPermissionForWarehouse(anyString(), anyLong())).thenReturn(true);
    }

    private NotificationChannelConfig seedConfig() {
        NotificationChannelConfig config = new NotificationChannelConfig();
        config.setCenterId(1L);
        config.setAlertType("TEMPERATURE");
        config.setActive(true);
        config.setChannels(List.of(
                new NotificationChannelConfig.ChannelEntry(
                        NotificationChannelConfig.ChannelType.EMAIL, true, null)));
        return configRepository.save(config);
    }

    /**
     * Verifies that listing configs returns HTTP 200 with a JSON array.
     */
    @Test
    void listConfigsReturns200() throws Exception {
        stubPermissions();
        seedConfig();

        mockMvc.perform(get("/api/v1/notification-channel-configs?centerId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));
    }

    /**
     * Verifies that retrieving a config by ID returns HTTP 200 with correct alert type.
     */
    @Test
    void getConfigByIdReturns200() throws Exception {
        stubPermissions();
        NotificationChannelConfig config = seedConfig();

        mockMvc.perform(get("/api/v1/notification-channel-configs/{id}", config.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertType").value("TEMPERATURE"))
                .andExpect(jsonPath("$.active").value(true));
    }

    /**
     * Verifies that resolving a config returns HTTP 200 when a matching config exists.
     */
    @Test
    void resolveConfigReturns200() throws Exception {
        stubPermissions();
        seedConfig();

        mockMvc.perform(get("/api/v1/notification-channel-configs/resolve?centerId=1&alertType=TEMPERATURE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertType").value("TEMPERATURE"));
    }

    /**
     * Verifies that resolving a non-existent config returns HTTP 404.
     */
    @Test
    void resolveConfigReturns404() throws Exception {
        stubPermissions();

        mockMvc.perform(get("/api/v1/notification-channel-configs/resolve?centerId=999&alertType=UNKNOWN"))
                .andExpect(status().isNotFound());
    }

    /**
     * Verifies that creating a config returns HTTP 200 with persisted data.
     */
    @Test
    void createConfigReturns200() throws Exception {
        stubPermissions();
        String json = """
                {"centerId":1,"warehouseId":null,"alertType":"HUMIDITY","active":true,"channels":[{"type":"EMAIL","enabled":true,"webhookProvider":null}]}
                """;

        mockMvc.perform(post("/api/v1/notification-channel-configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertType").value("HUMIDITY"))
                .andExpect(jsonPath("$.channels").isArray());
    }

    /**
     * Verifies that updating a config returns HTTP 200 with updated data.
     */
    @Test
    void updateConfigReturns200() throws Exception {
        stubPermissions();
        NotificationChannelConfig config = seedConfig();
        String json = """
                {"centerId":1,"warehouseId":null,"alertType":"UPDATED_TYPE","active":false,"channels":[]}
                """;

        mockMvc.perform(put("/api/v1/notification-channel-configs/{id}", config.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertType").value("UPDATED_TYPE"))
                .andExpect(jsonPath("$.active").value(false));
    }

    /**
     * Verifies that deleting a config returns HTTP 204 and soft-deletes the record.
     */
    @Test
    void deleteConfigReturns204() throws Exception {
        stubPermissions();
        NotificationChannelConfig config = seedConfig();
        Long id = config.getId();

        mockMvc.perform(delete("/api/v1/notification-channel-configs/{id}", id))
                .andExpect(status().isNoContent());

        assertThat(configRepository.findById(id))
                .isPresent()
                .hasValueSatisfying(c -> assertThat(c.isActive()).isFalse());
    }

    /**
     * Verifies that testing a webhook returns HTTP 200.
     * External webhook call is mocked to avoid real network requests.
     */
    @Test
    void testWebhookReturns200() throws Exception {
        stubPermissions();
        doNothing().when(webhookService).send(anyString(), anyString(), any());

        NotificationChannelConfig config = new NotificationChannelConfig();
        config.setCenterId(1L);
        config.setAlertType("TEMPERATURE");
        config.setActive(true);
        config.setChannels(List.of(
                new NotificationChannelConfig.ChannelEntry(
                        NotificationChannelConfig.ChannelType.WEBHOOK, true, "SLACK")));
        config = configRepository.save(config);

        WebhookEndpointConfig endpoint = new WebhookEndpointConfig();
        endpoint.setCenterId(1L);
        endpoint.setProviderType(WebhookEndpointConfig.WebhookProviderType.SLACK);
        endpoint.setWebhookUrl("");
        endpoint.setEnabled(true);
        webhookEndpointConfigRepository.save(endpoint);

        mockMvc.perform(post("/api/v1/notification-channel-configs/{id}/test-webhook", config.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
