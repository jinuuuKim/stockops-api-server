package com.stockops.notification.webhook;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link NotificationDeliveryLog}.
 *
 * @author StockOps Team
 * @since 2.3
 */
public interface NotificationDeliveryLogRepository extends JpaRepository<NotificationDeliveryLog, Long> {
}
