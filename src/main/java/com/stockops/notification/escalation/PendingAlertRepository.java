package com.stockops.notification.escalation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for PendingAlert entities with status-based and time-based lookups.
 *
 * @author StockOps Team
 * @since 2.0
 * @see PendingAlert
 * @see PendingAlertStatus
 */
@Repository
public interface PendingAlertRepository extends JpaRepository<PendingAlert, Long> {

    /**
     * Finds all pending alerts with the given status.
     * Used by the scheduler to find alerts eligible for escalation.
     *
     * @param status alert status to filter by
     * @return matching alerts
     */
    List<PendingAlert> findByStatus(PendingAlertStatus status);

    /**
     * Finds pending alerts created before the given timestamp.
     * Used to find alerts whose escalation delay has elapsed.
     *
     * @param status    alert status to filter by
     * @param timestamp cutoff timestamp
     * @return matching alerts
     */
    List<PendingAlert> findByStatusAndCreatedAtBefore(PendingAlertStatus status, Instant timestamp);

    /**
     * Finds a pending alert by ID only if it is still in PENDING status.
     * Used for atomic acknowledgment to prevent double-ack.
     *
     * @param id alert identifier
     * @return matching alert if found and still pending
     */
    Optional<PendingAlert> findByIdAndStatus(Long id, PendingAlertStatus status);
}