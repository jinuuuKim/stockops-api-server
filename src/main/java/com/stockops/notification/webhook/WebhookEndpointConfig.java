package com.stockops.notification.webhook;

import com.stockops.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Persistent configuration for a webhook endpoint.
 * Stores the target URL, provider type, and optional extra config (JSON)
 * per center/warehouse scope.
 *
 * @author StockOps Team
 * @since 1.0
 */
@Data
@Entity
@Table(name = "webhook_endpoint_config")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WebhookEndpointConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "center_id")
    private Long centerId;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 20)
    private WebhookProviderType providerType;

    @Column(name = "webhook_url", nullable = false, length = 2048)
    private String webhookUrl;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /**
     * JSON string for provider-specific configuration such as
     * custom headers, secrets, or template overrides.
     * Stored as TEXT to accommodate complex configurations.
     */
    @Column(name = "extra_config", columnDefinition = "TEXT")
    private String extraConfig;

    /**
     * Supported webhook provider types.
     */
    public enum WebhookProviderType {
        SLACK,
        NOTION,
        DISCORD,
        TEAMS,
        GENERIC
    }
}