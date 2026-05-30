package com.stockops.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockops.entity.ai.AISuggestion;
import com.stockops.entity.ai.AISuggestionAudit;
import com.stockops.entity.ai.AISuggestionStatus;
import com.stockops.repository.ai.AISuggestionAuditRepository;
import com.stockops.repository.ai.AISuggestionRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AISuggestionAuditServiceTest {

    @Autowired
    private AISuggestionRepository aiSuggestionRepository;

    @Autowired
    private AISuggestionAuditRepository aiSuggestionAuditRepository;

    @Autowired
    private AISuggestionAuditService aiSuggestionAuditService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void recordCreatePersistsAuditFields() {
        final AISuggestion suggestion = savedSuggestion("PURCHASE_ORDER_CREATE", "AI_AGENT");

        aiSuggestionAuditService.recordCreate(suggestion, new AISuggestionAuditService.AuditActor(7L, "Planner", "AI_ADMIN"), "req-create-1");

        final AISuggestionAudit audit = auditsFor(suggestion.getId()).get(0);
        assertThat(audit.getAction()).isEqualTo("CREATE");
        assertThat(audit.getSuggestionId()).isEqualTo(suggestion.getId());
        assertThat(audit.getSourceType()).isEqualTo("AI_AGENT");
        assertThat(audit.getApprovalMode()).isEqualTo("MANUAL_APPROVAL_REQUIRED");
        assertThat(audit.getBeforeStatus()).isNull();
        assertThat(audit.getAfterStatus()).isEqualTo("PENDING");
        assertThat(audit.getActorUserId()).isEqualTo(7L);
        assertThat(audit.getActorRole()).isEqualTo("AI_ADMIN");
        assertThat(audit.getTargetType()).isEqualTo("PRODUCT");
        assertThat(audit.getTargetScopeType()).isEqualTo("WAREHOUSE");
        assertThat(audit.getRequestId()).isEqualTo("req-create-1");
        assertThat(audit.getResult()).isEqualTo("SUCCESS");
        assertThat(audit.getErrorMessage()).isNull();
        assertThat(audit.getRecordedAt()).isNotNull();
    }

    @Test
    void recordApprovePersistsBeforeAndAfterState() {
        final AISuggestion before = savedSuggestion("PURCHASE_ORDER_CREATE", "AI_AGENT");
        before.setStatus(AISuggestionStatus.PENDING);
        final AISuggestion after = savedSuggestion("PURCHASE_ORDER_CREATE", "AI_AGENT");
        after.setStatus(AISuggestionStatus.APPROVED);

        aiSuggestionAuditService.recordApprove(before, after, new AISuggestionAuditService.AuditActor(11L, "Manager", "BRANCH_MANAGER"), "req-approve-1");

        final AISuggestionAudit audit = auditsFor(before.getId()).get(0);
        assertThat(audit.getAction()).isEqualTo("APPROVE");
        assertThat(audit.getBeforeStatus()).isEqualTo("PENDING");
        assertThat(audit.getAfterStatus()).isEqualTo("APPROVED");
        assertThat(audit.getPreviousStatus()).isEqualTo("PENDING");
        assertThat(audit.getNextStatus()).isEqualTo("APPROVED");
        assertThat(audit.getBeforePayloadSummary()).contains("payload=");
        assertThat(audit.getAfterPayloadSummary()).contains("payload=");
        assertThat(audit.getActorName()).isEqualTo("Manager");
        assertThat(audit.getTargetId()).isEqualTo(123L);
    }

    @Test
    void recordRejectPersistsReasonAndScope() {
        final AISuggestion before = savedSuggestion("PURCHASE_ORDER_CREATE", "AI_AGENT");
        final AISuggestion after = savedSuggestion("PURCHASE_ORDER_CREATE", "AI_AGENT");
        after.setStatus(AISuggestionStatus.REJECTED);

        aiSuggestionAuditService.recordReject(before, after, new AISuggestionAuditService.AuditActor(12L, "Reviewer", "SALES_STAFF"), "req-reject-1", "duplicate request");

        final AISuggestionAudit audit = auditsFor(before.getId()).get(0);
        assertThat(audit.getAction()).isEqualTo("REJECT");
        assertThat(audit.getBeforeStatus()).isEqualTo("PENDING");
        assertThat(audit.getAfterStatus()).isEqualTo("REJECTED");
        assertThat(audit.getErrorMessage()).isEqualTo("duplicate request");
        assertThat(audit.getSourceType()).isEqualTo("AI_AGENT");
    }

    @Test
    void recordExecutePersistsSuccessResult() {
        final AISuggestion before = savedSuggestion("PURCHASE_ORDER_CREATE", "AI_AGENT");
        before.setStatus(AISuggestionStatus.APPROVED);
        final AISuggestion after = savedSuggestion("PURCHASE_ORDER_CREATE", "AI_AGENT");
        after.setStatus(AISuggestionStatus.EXECUTED);

        aiSuggestionAuditService.recordExecute(before, after, new AISuggestionAuditService.AuditActor(13L, "Operator", "GLOBAL_ADMIN"), "req-execute-1");

        final AISuggestionAudit audit = auditsFor(before.getId()).get(0);
        assertThat(audit.getAction()).isEqualTo("EXECUTE");
        assertThat(audit.getBeforeStatus()).isEqualTo("APPROVED");
        assertThat(audit.getAfterStatus()).isEqualTo("EXECUTED");
        assertThat(audit.getResult()).isEqualTo("SUCCESS");
        assertThat(audit.getErrorMessage()).isNull();
    }

    @Test
    void recordFailedExecutionPersistsFailureDetails() {
        final AISuggestion before = savedSuggestion("PURCHASE_ORDER_CREATE", "AI_AGENT");
        before.setStatus(AISuggestionStatus.APPROVED);
        final AISuggestion after = savedSuggestion("PURCHASE_ORDER_CREATE", "AI_AGENT");
        after.setStatus(AISuggestionStatus.FAILED);

        aiSuggestionAuditService.recordFailedExecution(before, after, new AISuggestionAuditService.AuditActor(14L, "Operator", "GLOBAL_ADMIN"), "req-failed-1", "inventory service unavailable");

        final AISuggestionAudit audit = auditsFor(before.getId()).get(0);
        assertThat(audit.getAction()).isEqualTo("EXECUTE_FAILED");
        assertThat(audit.getAfterStatus()).isEqualTo("FAILED");
        assertThat(audit.getResult()).isEqualTo("FAILURE");
        assertThat(audit.getErrorMessage()).isEqualTo("inventory service unavailable");
    }

    @Test
    void recordToolCreatedSuggestionPersistsToolAction() {
        final AISuggestion suggestion = savedSuggestion("FORECAST_ALERT", "BEDROCK_TOOL");

        aiSuggestionAuditService.recordToolCreatedSuggestion(suggestion, new AISuggestionAuditService.AuditActor(null, "Bedrock Tool", "SYSTEM"), null);

        final AISuggestionAudit audit = auditsFor(suggestion.getId()).get(0);
        assertThat(audit.getAction()).isEqualTo("TOOL_CREATE");
        assertThat(audit.getActorName()).isEqualTo("Bedrock Tool");
        assertThat(audit.getActorUserId()).isNull();
        assertThat(audit.getRequestId()).isNull();
        assertThat(audit.getSourceType()).isEqualTo("BEDROCK_TOOL");
    }

    private AISuggestion savedSuggestion(final String type, final String sourceType) {
        final AISuggestion suggestion = new AISuggestion();
        suggestion.setType(type);
        suggestion.setSeverity("HIGH");
        suggestion.setTitle("Restock milk");
        suggestion.setSummary("Milk forecast is below threshold");
        suggestion.setReason("Expected demand exceeds current stock");
        suggestion.setRecommendedAction("Create draft purchase order");
        suggestion.setTargetType("PRODUCT");
        suggestion.setTargetId(123L);
        suggestion.setTargetScopeType("WAREHOUSE");
        suggestion.setTargetScopeId(456L);
        suggestion.setPayloadJson("{\"quantity\":100}");
        suggestion.setSource("planner");
        suggestion.setSourceType(sourceType);
        suggestion.setVisibleToApp("BOTH");
        suggestion.setApprovalMode("MANUAL_APPROVAL_REQUIRED");
        suggestion.setStatus(AISuggestionStatus.PENDING);
        final AISuggestion saved = aiSuggestionRepository.save(suggestion);
        entityManager.flush();
        entityManager.clear();
        return aiSuggestionRepository.findById(saved.getId()).orElseThrow();
    }

    private List<AISuggestionAudit> auditsFor(final Long suggestionId) {
        entityManager.flush();
        entityManager.clear();
        return aiSuggestionAuditRepository.findBySuggestionIdOrderByRecordedAtAsc(suggestionId);
    }
}
