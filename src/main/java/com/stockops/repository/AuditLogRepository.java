package com.stockops.repository;

import com.stockops.entity.AuditLog;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for persisted audit logs.
 *
 * @author StockOps Team
 * @since 1.0
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Searches audit logs using optional filters.
     *
     * @param entityType audited entity type filter
     * @param entityId audited entity identifier filter
     * @param userId performer identifier filter
     * @param start start of the time range filter
     * @param end end of the time range filter
     * @param pageable pagination and sort request
     * @return matching audit logs ordered by recency
     */
    @Query("""
            select a from AuditLog a
            where (:entityType is null or a.entityType = :entityType)
              and (:entityId is null or a.entityId = :entityId)
              and (:userId is null or a.performedBy = :userId)
              and (:start is null or a.performedAt >= :start)
              and (:end is null or a.performedAt <= :end)
            order by a.performedAt desc
            """)
    List<AuditLog> findAuditLogs(@Param("entityType") String entityType,
                                 @Param("entityId") Long entityId,
                                 @Param("userId") Long userId,
                                 @Param("start") Instant start,
                                 @Param("end") Instant end,
                                 Pageable pageable);

    /**
     * Returns the most recent audit logs.
     *
     * @param pageable pagination and sort request
     * @return recent audit logs ordered by recency
     */
    @Query("select a from AuditLog a order by a.performedAt desc")
    List<AuditLog> findRecentAuditLogs(Pageable pageable);
}
