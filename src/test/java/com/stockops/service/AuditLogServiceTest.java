package com.stockops.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockops.dto.AuditLogDTO;
import com.stockops.entity.AuditLog;
import com.stockops.entity.User;
import com.stockops.repository.AuditLogRepository;
import com.stockops.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link AuditLogService}.
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    /**
     * Verifies that an unfiltered audit search uses the specification path instead of the null-parameter JPQL query.
     */
    @Test
    void searchAuditLogsSupportsUnfilteredRequests() {
        final AuditLogService auditLogService = new AuditLogService(auditLogRepository, userRepository);
        final AuditLog auditLog = new AuditLog();
        auditLog.setId(1L);
        auditLog.setEntityType("Product");
        auditLog.setEntityId(10L);
        auditLog.setAction("UPDATE");
        auditLog.setPerformedBy(2L);
        auditLog.setPerformedByEmail("admin@stockops.com");
        auditLog.setPerformedAt(Instant.parse("2026-05-19T07:40:00Z"));

        final User performer = new User();
        performer.setId(2L);
        performer.setName("StockOps Admin");

        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(auditLog)));
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(performer));

        final List<AuditLogDTO> results = auditLogService.searchAuditLogs(null, null, null, null, null);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.entityType()).isEqualTo("Product");
            assertThat(result.performedByName()).isEqualTo("StockOps Admin");
        });
        verify(auditLogRepository).findAll(any(Specification.class), any(Pageable.class));
    }
}
