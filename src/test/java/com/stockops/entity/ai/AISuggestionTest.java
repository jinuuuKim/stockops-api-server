package com.stockops.entity.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stockops.repository.ai.AISuggestionRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AISuggestionTest {

    @Autowired
    private AISuggestionRepository aiSuggestionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void newSuggestionDefaultsToPendingStatus() {
        final AISuggestion suggestion = new AISuggestion();

        assertThat(suggestion.getStatus()).isEqualTo(AISuggestionStatus.PENDING);
        assertThat(suggestion.canTransitionTo(AISuggestionStatus.APPROVED)).isTrue();
        assertThat(suggestion.canTransitionTo(AISuggestionStatus.REJECTED)).isTrue();
    }

    @Test
    void forbiddenTransitionsThrowExceptions() {
        final AISuggestion pending = new AISuggestion();
        final AISuggestion rejected = new AISuggestion();
        rejected.transitionTo(AISuggestionStatus.REJECTED);
        final AISuggestion executed = new AISuggestion();
        executed.transitionTo(AISuggestionStatus.APPROVED);
        executed.transitionTo(AISuggestionStatus.EXECUTED);
        final AISuggestion failed = new AISuggestion();
        failed.transitionTo(AISuggestionStatus.APPROVED);
        failed.transitionTo(AISuggestionStatus.FAILED);

        assertThatThrownBy(() -> pending.transitionTo(AISuggestionStatus.EXECUTED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING to EXECUTED");
        assertThatThrownBy(() -> rejected.transitionTo(AISuggestionStatus.APPROVED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REJECTED to APPROVED");
        assertThatThrownBy(() -> rejected.transitionTo(AISuggestionStatus.EXECUTED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REJECTED to EXECUTED");
        assertThatThrownBy(() -> executed.transitionTo(AISuggestionStatus.FAILED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EXECUTED to FAILED");
        assertThatThrownBy(() -> failed.transitionTo(AISuggestionStatus.EXECUTED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FAILED to EXECUTED");
    }

    @Test
    void requiredDocumentFieldsPersist() {
        final Instant forecastGeneratedAt = Instant.parse("2026-05-29T01:15:30Z");
        final Instant expiresAt = Instant.parse("2026-06-05T01:15:30Z");
        final Instant reviewedAt = Instant.parse("2026-05-29T02:15:30Z");
        final Instant approvedAt = Instant.parse("2026-05-29T03:15:30Z");
        final Instant executedAt = Instant.parse("2026-05-29T04:15:30Z");
        final AISuggestion suggestion = new AISuggestion();
        suggestion.setType("PURCHASE_ORDER_CREATE");
        suggestion.setSeverity("CRITICAL");
        suggestion.setTitle("Restock cola");
        suggestion.setSummary("Cola inventory is forecast below safety stock");
        suggestion.setReason("Forecasted demand exceeds available inventory");
        suggestion.setRecommendedAction("Create draft purchase order for 100 units");
        suggestion.setTargetType("PRODUCT");
        suggestion.setTargetId(123L);
        suggestion.setTargetScopeType("WAREHOUSE");
        suggestion.setTargetScopeId(456L);
        suggestion.setPayloadJson("{\"quantity\":100,\"product_id\":123}");
        suggestion.setConfidenceScore(0.87D);
        suggestion.setSource("procurement-agent");
        suggestion.setSourceType("AI_AGENT");
        suggestion.setCreatedByUserId(10L);
        suggestion.setCreatedFromApp("admin-web");
        suggestion.setForecastSourceType("PROPHET_TOOL");
        suggestion.setForecastSourceId(77L);
        suggestion.setForecastModelVersion("prophet-v1");
        suggestion.setForecastGeneratedAt(forecastGeneratedAt);
        suggestion.setForecastSourcePayloadJson("{\"forecast_total\":96}");
        suggestion.setVisibleToApp("BOTH");
        suggestion.setApprovalMode("MANUAL_APPROVAL_REQUIRED");
        suggestion.setRequestedOnBehalfUserId(20L);
        suggestion.setRequestedScopeType("BRANCH");
        suggestion.setRequestedScopeId(30L);
        suggestion.setExpiresAt(expiresAt);
        suggestion.setReviewedByUserId(40L);
        suggestion.setReviewedAt(reviewedAt);
        suggestion.setApprovedByUserId(50L);
        suggestion.setApprovedAt(approvedAt);
        suggestion.setExecutedAt(executedAt);
        suggestion.setRejectionReason("duplicate request");
        suggestion.setExecutionResult("{\"purchase_order_id\":999}");

        final AISuggestion saved = aiSuggestionRepository.save(suggestion);
        entityManager.flush();
        entityManager.clear();

        final AISuggestion reloaded = aiSuggestionRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getType()).isEqualTo("PURCHASE_ORDER_CREATE");
        assertThat(reloaded.getSeverity()).isEqualTo("CRITICAL");
        assertThat(reloaded.getTargetScopeType()).isEqualTo("WAREHOUSE");
        assertThat(reloaded.getTargetScopeId()).isEqualTo(456L);
        assertThat(reloaded.getPayloadJson()).contains("quantity");
        assertThat(reloaded.getSource()).isEqualTo("procurement-agent");
        assertThat(reloaded.getSourceType()).isEqualTo("AI_AGENT");
        assertThat(reloaded.getCreatedByUserId()).isEqualTo(10L);
        assertThat(reloaded.getCreatedFromApp()).isEqualTo("admin-web");
        assertThat(reloaded.getForecastSourceType()).isEqualTo("PROPHET_TOOL");
        assertThat(reloaded.getForecastSourceId()).isEqualTo(77L);
        assertThat(reloaded.getForecastModelVersion()).isEqualTo("prophet-v1");
        assertThat(reloaded.getForecastGeneratedAt()).isEqualTo(forecastGeneratedAt);
        assertThat(reloaded.getForecastSourcePayloadJson()).contains("forecast_total");
        assertThat(reloaded.getStatus()).isEqualTo(AISuggestionStatus.PENDING);
        assertThat(reloaded.getVisibleToApp()).isEqualTo("BOTH");
        assertThat(reloaded.getApprovalMode()).isEqualTo("MANUAL_APPROVAL_REQUIRED");
        assertThat(reloaded.getRequestedOnBehalfUserId()).isEqualTo(20L);
        assertThat(reloaded.getRequestedScopeType()).isEqualTo("BRANCH");
        assertThat(reloaded.getRequestedScopeId()).isEqualTo(30L);
        assertThat(reloaded.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
        assertThat(reloaded.getVersion()).isNotNull();
        assertThat(reloaded.getRejectionReason()).isEqualTo("duplicate request");
        assertThat(reloaded.getExecutionResult()).contains("purchase_order_id");
    }

    @Test
    void reviewPayloadJsonbFieldsPersistWithDefaults() {
        final AISuggestion suggestion = new AISuggestion();
        suggestion.setType("INVENTORY_REPLENISHMENT");
        suggestion.setSeverity("HIGH");
        suggestion.setTitle("Codex QA create debug");
        suggestion.setSummary("QA test suggestion");
        suggestion.setReason("Debug creation");
        suggestion.setRecommendedAction("No business write");
        suggestion.setTargetType("PRODUCT");
        suggestion.setTargetId(1L);
        suggestion.setTargetScopeType("CENTER");
        suggestion.setTargetScopeId(1L);
        suggestion.setPayloadJson("{\"qa\":true}");
        suggestion.setConfidenceScore(0.87D);
        suggestion.setSource("QA");
        suggestion.setSourceType("USER_REQUEST");
        suggestion.setCreatedFromApp("admin-web");
        suggestion.setVisibleToApp("ADMIN_WEB");
        suggestion.setApprovalMode("MANUAL");
        suggestion.setRequestedScopeType("CENTER");
        suggestion.setRequestedScopeId(1L);

        final AISuggestion saved = aiSuggestionRepository.save(suggestion);
        entityManager.flush();
        entityManager.clear();

        final AISuggestion reloaded = aiSuggestionRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getPayloadJson()).contains("qa");
        assertThat(reloaded.getForecastSourcePayloadJson()).isEqualTo("{}");
        assertThat(reloaded.getExecutionResult()).isEqualTo("{}");
    }
}
