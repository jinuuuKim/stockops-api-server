package com.stockops.environment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.stockops.entity.AlertSeverity;
import com.stockops.entity.Center;
import com.stockops.entity.EnvironmentAlert;
import com.stockops.entity.SensorDevice;
import com.stockops.entity.Warehouse;
import com.stockops.notification.webhook.WebhookEndpointConfig;
import com.stockops.notification.webhook.WebhookEndpointConfig.WebhookProviderType;
import com.stockops.notification.webhook.WebhookEndpointConfigRepository;
import com.stockops.notification.webhook.WebhookPayload;
import com.stockops.notification.webhook.WebhookService;
import com.stockops.repository.WarehouseRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link EnvironmentAlertNotifier} scoped webhook routing.
 *
 * @author StockOps Team
 * @since 2.3
 */
@ExtendWith(MockitoExtension.class)
class EnvironmentAlertNotifierTest {

    @Mock
    private WebhookService webhookService;

    @Mock
    private WebhookEndpointConfigRepository webhookEndpointConfigRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @InjectMocks
    private EnvironmentAlertNotifier notifier;

    /**
     * Verifies the alert is delivered to the sensor's warehouse endpoint and its center-level
     * endpoint only — never via the legacy broadcast.
     */
    @Test
    void dispatchesToWarehouseAndCenterLevelEndpointsOnly() {
        final SensorDevice device = sensor(100L, 10L);
        when(warehouseRepository.findByIdWithCenter(10L)).thenReturn(Optional.of(warehouse(10L, 5L, "강남창고")));
        when(webhookEndpointConfigRepository.findByWarehouseIdAndEnabledTrue(10L))
                .thenReturn(List.of(endpoint(1L, "wh-url")));
        when(webhookEndpointConfigRepository.findByCenterIdAndWarehouseIdIsNullAndEnabledTrue(5L))
                .thenReturn(List.of(endpoint(2L, "center-url")));

        notifier.dispatch(alert(7L, AlertSeverity.CRITICAL), device);

        verify(webhookService).send(eq("TEAMS"), eq("wh-url"), any(WebhookPayload.class));
        verify(webhookService).send(eq("TEAMS"), eq("center-url"), any(WebhookPayload.class));
        verify(webhookService, times(2)).send(any(), any(), any(WebhookPayload.class));
        verify(webhookEndpointConfigRepository, never()).findByEnabledTrue();
    }

    /**
     * Verifies a sensor without a warehouse is skipped (no broadcast fallback).
     */
    @Test
    void skipsWhenSensorHasNoWarehouse() {
        notifier.dispatch(alert(7L, AlertSeverity.WARNING), sensor(100L, null));

        verifyNoInteractions(webhookService);
    }

    private SensorDevice sensor(final Long id, final Long warehouseId) {
        final SensorDevice sensor = new SensorDevice();
        sensor.setId(id);
        sensor.setWarehouseId(warehouseId);
        return sensor;
    }

    private Warehouse warehouse(final Long id, final Long centerId, final String name) {
        final Center center = new Center();
        center.setId(centerId);
        final Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setName(name);
        warehouse.setCenter(center);
        return warehouse;
    }

    private WebhookEndpointConfig endpoint(final Long id, final String url) {
        final WebhookEndpointConfig endpoint = new WebhookEndpointConfig();
        endpoint.setId(id);
        endpoint.setProviderType(WebhookProviderType.TEAMS);
        endpoint.setWebhookUrl(url);
        endpoint.setEnabled(true);
        return endpoint;
    }

    private EnvironmentAlert alert(final Long id, final AlertSeverity severity) {
        final EnvironmentAlert alert = new EnvironmentAlert();
        alert.setId(id);
        alert.setSeverity(severity);
        alert.setMessage("msg");
        alert.setAlertType("TEMPERATURE");
        return alert;
    }
}
