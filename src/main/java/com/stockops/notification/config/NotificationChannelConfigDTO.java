package com.stockops.notification.config;

import java.time.Instant;
import java.util.List;

/**
 * DTO for NotificationChannelConfig responses.
 *
 * @author StockOps Team
 * @since 2.0
 */
public record NotificationChannelConfigDTO(
        Long id,
        Long centerId,
        Long warehouseId,
        String alertType,
        List<ChannelEntryDTO> channels,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}

/**
 * DTO for a single channel entry within a config.
 *
 * @author StockOps Team
 * @since 2.0
 */
record ChannelEntryDTO(
        String type,
        boolean enabled,
        String webhookProvider
) {}

/**
 * DTO for creating/updating a NotificationChannelConfig.
 *
 * @author StockOps Team
 * @since 2.0
 */
record NotificationChannelConfigRequest(
        Long centerId,
        Long warehouseId,
        String alertType,
        List<ChannelEntryRequest> channels,
        Boolean active
) {}

/**
 * DTO for a channel entry in create/update requests.
 *
 * @author StockOps Team
 * @since 2.0
 */
record ChannelEntryRequest(
        String type,
        boolean enabled,
        String webhookProvider,
        String webhookUrl
) {}

/**
 * DTO for webhook test result.
 *
 * @author StockOps Team
 * @since 2.0
 */
record WebhookTestResult(
        boolean success,
        String message,
        String providerType
) {}
