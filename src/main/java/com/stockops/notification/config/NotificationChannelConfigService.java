package com.stockops.notification.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.stockops.exception.ResourceNotFoundException;
import com.stockops.notification.webhook.WebhookEndpointConfig;
import com.stockops.notification.webhook.WebhookEndpointConfigRepository;
import com.stockops.notification.webhook.WebhookPayload;
import com.stockops.notification.webhook.WebhookService;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing notification channel configurations.
 * Handles CRUD operations, channel resolution, and webhook testing.
 *
 * <p>Resolution logic: warehouse-specific config takes precedence
 * over center-level config. If no config is found, default channels
 * (in-app only) are returned.</p>
 *
 * @author StockOps Team
 * @since 2.0
 * @see NotificationChannelConfig
 */
@Service
@Transactional
public class NotificationChannelConfigService {

    private final NotificationChannelConfigRepository configRepository;
    private final WebhookEndpointConfigRepository webhookEndpointConfigRepository;
    private final WebhookService webhookService;

    @Transactional(readOnly = true)
    public List<NotificationChannelConfig> findAllByCenterId(Long centerId) {
        return configRepository.findByCenterIdAndActiveTrue(centerId);
    }

    @Transactional(readOnly = true)
    public NotificationChannelConfig findById(Long id) {
        return configRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("NotificationChannelConfig not found: " + id));
    }

    /**
     * Resolves the most specific channel config for a given alert context.
     * Prefers warehouse-specific config over center-level fallback.
     *
     * @param centerId    center identifier
     * @param warehouseId warehouse identifier (nullable for center-level lookup)
     * @param alertType   alert type string
     * @return matching active config, or empty if none found
     */
    @Transactional(readOnly = true)
    public Optional<NotificationChannelConfig> resolveChannels(Long centerId, Long warehouseId, String alertType) {
        if (warehouseId != null) {
            Optional<NotificationChannelConfig> warehouseConfig =
                    configRepository.findByCenterIdAndWarehouseIdAndAlertTypeAndActiveTrue(
                            centerId, warehouseId, alertType);
            if (warehouseConfig.isPresent()) {
                return warehouseConfig;
            }
        }
        return configRepository.findByCenterIdAndWarehouseIdIsNullAndAlertTypeAndActiveTrue(centerId, alertType);
    }

    /**
     * Creates a new notification channel config.
     *
     * @param request creation request
     * @return created config
     * @throws IllegalArgumentException if duplicate scope config already exists
     */
    public NotificationChannelConfig create(NotificationChannelConfigRequest request) {
        NotificationChannelConfig config = new NotificationChannelConfig();
        config.setCenterId(request.centerId());
        config.setWarehouseId(request.warehouseId());
        config.setAlertType(request.alertType());
        config.setActive(request.active() != null ? request.active() : true);

        if (request.channels() != null) {
            config.setChannels(request.channels().stream()
                    .map(ch -> new NotificationChannelConfig.ChannelEntry(
                            NotificationChannelConfig.ChannelType.valueOf(ch.type()),
                            ch.enabled(),
                            ch.webhookProvider()))
                    .toList());
        } else {
            config.setChannels(List.of());
        }

        return configRepository.save(config);
    }

    /**
     * Updates an existing notification channel config.
     *
     * @param id      config identifier
     * @param request update request
     * @return updated config
     * @throws ResourceNotFoundException if config not found
     */
    public NotificationChannelConfig update(Long id, NotificationChannelConfigRequest request) {
        NotificationChannelConfig config = findById(id);

        config.setCenterId(request.centerId());
        config.setWarehouseId(request.warehouseId());
        config.setAlertType(request.alertType());
        if (request.active() != null) {
            config.setActive(request.active());
        }

        if (request.channels() != null) {
            config.setChannels(request.channels().stream()
                    .map(ch -> new NotificationChannelConfig.ChannelEntry(
                            NotificationChannelConfig.ChannelType.valueOf(ch.type()),
                            ch.enabled(),
                            ch.webhookProvider()))
                    .toList());
        } else {
            config.setChannels(List.of());
        }

        return configRepository.save(config);
    }

    /**
     * Soft-deletes a config by setting active = false.
     *
     * @param id config identifier
     * @throws ResourceNotFoundException if config not found
     */
    public void delete(Long id) {
        NotificationChannelConfig config = findById(id);
        config.setActive(false);
        configRepository.save(config);
    }

    /**
     * Sends a test webhook payload to verify connectivity.
     * Uses the webhook endpoint config associated with the channel config's
     * webhook provider to send a test notification.
     *
     * @param configId notification channel config identifier
     * @return test result indicating success or failure
     * @throws ResourceNotFoundException if config not found
     */
    public WebhookTestResult testWebhook(Long configId) {
        NotificationChannelConfig config = findById(configId);

        NotificationChannelConfig.ChannelEntry webhookEntry = config.getChannels().stream()
                .filter(ch -> ch.getType() == NotificationChannelConfig.ChannelType.WEBHOOK && ch.isEnabled())
                .findFirst()
                .orElse(null);

        if (webhookEntry == null) {
            return new WebhookTestResult(false, "No enabled WEBHOOK channel found in config", null);
        }

        String providerType = webhookEntry.getWebhookProvider();
        if (providerType == null || providerType.isBlank()) {
            return new WebhookTestResult(false, "Webhook provider type not specified", null);
        }

        List<WebhookEndpointConfig> endpoints = webhookEndpointConfigRepository
                .findByCenterIdAndProviderTypeAndEnabledTrue(
                        config.getCenterId(),
                        WebhookEndpointConfig.WebhookProviderType.valueOf(providerType));

        if (endpoints.isEmpty()) {
            return new WebhookTestResult(false,
                    "No enabled webhook endpoint found for provider: " + providerType, providerType);
        }

        WebhookEndpointConfig endpoint = endpoints.get(0);
        WebhookPayload testPayload = WebhookPayload.builder()
                .eventType("TEST")
                .message("StockOps webhook test notification")
                .severity(WebhookPayload.Severity.INFO)
                .alertType(config.getAlertType())
                .timestamp(java.time.Instant.now())
                .details(java.util.Map.of("test", "true", "configId", String.valueOf(configId)))
                .build();

        try {
            webhookService.send(providerType, endpoint.getWebhookUrl(), testPayload);
            return new WebhookTestResult(true, "Test webhook sent successfully to masked endpoint", providerType);
        } catch (Exception e) {
            log.error("Webhook test failed for configId={}: {}", configId, e.getMessage(), e);
            return new WebhookTestResult(false, "Webhook test failed for provider: " + providerType, providerType);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(NotificationChannelConfigService.class);

    public NotificationChannelConfigService(final NotificationChannelConfigRepository configRepository, final WebhookEndpointConfigRepository webhookEndpointConfigRepository, final WebhookService webhookService) {
        this.configRepository = configRepository;
        this.webhookEndpointConfigRepository = webhookEndpointConfigRepository;
        this.webhookService = webhookService;
    }
}
