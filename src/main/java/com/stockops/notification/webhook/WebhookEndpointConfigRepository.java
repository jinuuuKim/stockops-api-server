package com.stockops.notification.webhook;

import com.stockops.notification.webhook.WebhookEndpointConfig.WebhookProviderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for webhook endpoint configuration persistence.
 *
 * @author StockOps Team
 * @since 1.0
 */
@Repository
public interface WebhookEndpointConfigRepository extends JpaRepository<WebhookEndpointConfig, Long> {

    /**
     * Finds all enabled endpoints for a given center.
     *
     * @param centerId center identifier
     * @return list of enabled endpoint configs for the center
     */
    List<WebhookEndpointConfig> findByCenterIdAndEnabledTrue(Long centerId);

    /**
     * Finds all enabled endpoints for a given warehouse.
     *
     * @param warehouseId warehouse identifier
     * @return list of enabled endpoint configs for the warehouse
     */
    List<WebhookEndpointConfig> findByWarehouseIdAndEnabledTrue(Long warehouseId);

    /**
     * Finds all enabled endpoints for a specific provider type.
     *
     * @param providerType webhook provider type
     * @return list of enabled endpoint configs matching the provider type
     */
    List<WebhookEndpointConfig> findByProviderTypeAndEnabledTrue(WebhookProviderType providerType);

    /**
     * Finds all enabled endpoints for a center and provider type.
     *
     * @param centerId     center identifier
     * @param providerType webhook provider type
     * @return list of matching enabled endpoint configs
     */
    List<WebhookEndpointConfig> findByCenterIdAndProviderTypeAndEnabledTrue(
            Long centerId, WebhookProviderType providerType);
}