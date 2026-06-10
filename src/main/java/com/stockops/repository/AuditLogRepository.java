package com.stockops.repository;

import com.stockops.entity.AuditLog;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for persisted audit logs.
 *
 * @author StockOps Team
 * @since 1.0
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

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
    @Query("select a from AuditLog a where a.entityType <> 'SensorReading' order by a.performedAt desc")
    List<AuditLog> findRecentAuditLogs(Pageable pageable);

    /**
     * Counts audit log entries whose entity type is in the given list and whose
     * {@code performedAt} timestamp is strictly after {@code since}.
     *
     * <p>Used by {@link com.stockops.ai.bedrock.BedrockAiFacade} to include
     * privilege-sensitive change volume in the daily ops summary
     * (design doc §3.2 candidate: 권한상 민감한 감사 로그 이벤트).
     * Typical caller passes entity types {@code [User, Role, Permission, RolePermission]}
     * and a 24-hour look-back window.
     *
     * @param entityTypes entity type simple names to match (e.g. "User", "Role")
     * @param since       lower bound for {@code performedAt} (exclusive)
     * @return count of matching audit log entries
     */
    long countByEntityTypeInAndPerformedAtAfter(List<String> entityTypes, Instant since);
}
