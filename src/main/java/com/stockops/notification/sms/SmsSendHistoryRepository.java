package com.stockops.notification.sms;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for SMS send history persistence.
 *
 * @author StockOps Team
 * @since 1.0
 */
@Repository
public interface SmsSendHistoryRepository extends JpaRepository<SmsSendHistory, Long> {

    /**
     * Finds history entries by destination phone number ordered by recency.
     *
     * @param phoneNumber target phone number
     * @return matching entries
     */
    List<SmsSendHistory> findByPhoneNumberOrderByCreatedAtDesc(String phoneNumber);

    /**
     * Finds a history entry by provider message id.
     *
     * @param messageId provider message id
     * @return matching entry when present
     */
    Optional<SmsSendHistory> findByMessageId(String messageId);

    /**
     * Returns paginated history for a phone number.
     *
     * @param phoneNumber target phone number
     * @param pageable   pagination params
     * @return page of matching entries
     */
    Page<SmsSendHistory> findByPhoneNumber(String phoneNumber, Pageable pageable);

    /**
     * Counts failed sends in the last N minutes for rate-limit monitoring.
     *
     * @param phoneNumber target phone number
     * @param since       cutoff instant
     * @return failure count
     */
    @Query("SELECT COUNT(s) FROM SmsSendHistory s WHERE s.phoneNumber = :phone AND s.status = 'FAILURE' AND s.createdAt >= :since")
    long countRecentFailures(@Param("phone") String phoneNumber, @Param("since") Instant since);
}
