package com.stockops.entity.ai;

import com.stockops.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "analytics", name = "ai_suggestions")
public class AISuggestion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "type", nullable = false, length = 100)
    private String type;

    @Column(name = "severity", nullable = false, length = 50)
    private String severity;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "summary", length = 500)
    private String summary;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "recommended_action", columnDefinition = "TEXT")
    private String recommendedAction;

    @Column(name = "target_type", length = 100)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "target_scope_type", nullable = false, length = 50)
    private String targetScopeType;

    @Column(name = "target_scope_id", nullable = false)
    private Long targetScopeId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson = "{}";

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "source", nullable = false, length = 100)
    private String source;

    @Column(name = "source_type", nullable = false, length = 100)
    private String sourceType;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_from_app", length = 100)
    private String createdFromApp;

    @Column(name = "forecast_source_type", length = 100)
    private String forecastSourceType;

    @Column(name = "forecast_source_id")
    private Long forecastSourceId;

    @Column(name = "forecast_model_version", length = 100)
    private String forecastModelVersion;

    @Column(name = "forecast_generated_at")
    private Instant forecastGeneratedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "forecast_source_payload_json", columnDefinition = "jsonb")
    private String forecastSourcePayloadJson = "{}";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private AISuggestionStatus status = AISuggestionStatus.PENDING;

    @Column(name = "visible_to_app", nullable = false, length = 50)
    private String visibleToApp;

    @Column(name = "approval_mode", nullable = false, length = 100)
    private String approvalMode;

    @Column(name = "requested_on_behalf_user_id")
    private Long requestedOnBehalfUserId;

    @Column(name = "requested_scope_type", length = 50)
    private String requestedScopeType;

    @Column(name = "requested_scope_id")
    private Long requestedScopeId;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "approved_by_user_id")
    private Long approvedByUserId;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "execution_result", columnDefinition = "jsonb")
    private String executionResult = "{}";

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public AISuggestion() {
    }

    public void transitionTo(final AISuggestionStatus nextStatus) {
        if (nextStatus == null) {
            throw new IllegalArgumentException("nextStatus must not be null");
        }
        if (!isTransitionAllowed(this.status, nextStatus)) {
            throw new IllegalStateException("Cannot transition AI suggestion from "
                    + this.status + " to " + nextStatus);
        }
        this.status = nextStatus;
    }

    public boolean canTransitionTo(final AISuggestionStatus nextStatus) {
        return nextStatus != null && isTransitionAllowed(this.status, nextStatus);
    }

    private static boolean isTransitionAllowed(final AISuggestionStatus currentStatus,
                                               final AISuggestionStatus nextStatus) {
        return switch (currentStatus) {
            case PENDING -> nextStatus == AISuggestionStatus.APPROVED
                    || nextStatus == AISuggestionStatus.REJECTED;
            case APPROVED -> nextStatus == AISuggestionStatus.EXECUTED
                    || nextStatus == AISuggestionStatus.FAILED;
            case REJECTED, EXECUTED, FAILED -> false;
        };
    }

    public Long getId() {
        return this.id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public String getType() {
        return this.type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public String getSeverity() {
        return this.severity;
    }

    public void setSeverity(final String severity) {
        this.severity = severity;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    public String getSummary() {
        return this.summary;
    }

    public void setSummary(final String summary) {
        this.summary = summary;
    }

    public String getReason() {
        return this.reason;
    }

    public void setReason(final String reason) {
        this.reason = reason;
    }

    public String getRecommendedAction() {
        return this.recommendedAction;
    }

    public void setRecommendedAction(final String recommendedAction) {
        this.recommendedAction = recommendedAction;
    }

    public String getTargetType() {
        return this.targetType;
    }

    public void setTargetType(final String targetType) {
        this.targetType = targetType;
    }

    public Long getTargetId() {
        return this.targetId;
    }

    public void setTargetId(final Long targetId) {
        this.targetId = targetId;
    }

    public String getTargetScopeType() {
        return this.targetScopeType;
    }

    public void setTargetScopeType(final String targetScopeType) {
        this.targetScopeType = targetScopeType;
    }

    public Long getTargetScopeId() {
        return this.targetScopeId;
    }

    public void setTargetScopeId(final Long targetScopeId) {
        this.targetScopeId = targetScopeId;
    }

    public String getPayloadJson() {
        return this.payloadJson;
    }

    public void setPayloadJson(final String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public Double getConfidenceScore() {
        return this.confidenceScore;
    }

    public void setConfidenceScore(final Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getSource() {
        return this.source;
    }

    public void setSource(final String source) {
        this.source = source;
    }

    public String getSourceType() {
        return this.sourceType;
    }

    public void setSourceType(final String sourceType) {
        this.sourceType = sourceType;
    }

    public Long getCreatedByUserId() {
        return this.createdByUserId;
    }

    public void setCreatedByUserId(final Long createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public String getCreatedFromApp() {
        return this.createdFromApp;
    }

    public void setCreatedFromApp(final String createdFromApp) {
        this.createdFromApp = createdFromApp;
    }

    public String getForecastSourceType() {
        return this.forecastSourceType;
    }

    public void setForecastSourceType(final String forecastSourceType) {
        this.forecastSourceType = forecastSourceType;
    }

    public Long getForecastSourceId() {
        return this.forecastSourceId;
    }

    public void setForecastSourceId(final Long forecastSourceId) {
        this.forecastSourceId = forecastSourceId;
    }

    public String getForecastModelVersion() {
        return this.forecastModelVersion;
    }

    public void setForecastModelVersion(final String forecastModelVersion) {
        this.forecastModelVersion = forecastModelVersion;
    }

    public Instant getForecastGeneratedAt() {
        return this.forecastGeneratedAt;
    }

    public void setForecastGeneratedAt(final Instant forecastGeneratedAt) {
        this.forecastGeneratedAt = forecastGeneratedAt;
    }

    public String getForecastSourcePayloadJson() {
        return this.forecastSourcePayloadJson;
    }

    public void setForecastSourcePayloadJson(final String forecastSourcePayloadJson) {
        this.forecastSourcePayloadJson = forecastSourcePayloadJson;
    }

    public AISuggestionStatus getStatus() {
        return this.status;
    }

    public void setStatus(final AISuggestionStatus status) {
        this.status = status;
    }

    public String getVisibleToApp() {
        return this.visibleToApp;
    }

    public void setVisibleToApp(final String visibleToApp) {
        this.visibleToApp = visibleToApp;
    }

    public String getApprovalMode() {
        return this.approvalMode;
    }

    public void setApprovalMode(final String approvalMode) {
        this.approvalMode = approvalMode;
    }

    public Long getRequestedOnBehalfUserId() {
        return this.requestedOnBehalfUserId;
    }

    public void setRequestedOnBehalfUserId(final Long requestedOnBehalfUserId) {
        this.requestedOnBehalfUserId = requestedOnBehalfUserId;
    }

    public String getRequestedScopeType() {
        return this.requestedScopeType;
    }

    public void setRequestedScopeType(final String requestedScopeType) {
        this.requestedScopeType = requestedScopeType;
    }

    public Long getRequestedScopeId() {
        return this.requestedScopeId;
    }

    public void setRequestedScopeId(final Long requestedScopeId) {
        this.requestedScopeId = requestedScopeId;
    }

    public Instant getExpiresAt() {
        return this.expiresAt;
    }

    public void setExpiresAt(final Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Long getReviewedByUserId() {
        return this.reviewedByUserId;
    }

    public void setReviewedByUserId(final Long reviewedByUserId) {
        this.reviewedByUserId = reviewedByUserId;
    }

    public Instant getReviewedAt() {
        return this.reviewedAt;
    }

    public void setReviewedAt(final Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public Long getApprovedByUserId() {
        return this.approvedByUserId;
    }

    public void setApprovedByUserId(final Long approvedByUserId) {
        this.approvedByUserId = approvedByUserId;
    }

    public Instant getApprovedAt() {
        return this.approvedAt;
    }

    public void setApprovedAt(final Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public Instant getExecutedAt() {
        return this.executedAt;
    }

    public void setExecutedAt(final Instant executedAt) {
        this.executedAt = executedAt;
    }

    public String getRejectionReason() {
        return this.rejectionReason;
    }

    public void setRejectionReason(final String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getExecutionResult() {
        return this.executionResult;
    }

    public void setExecutionResult(final String executionResult) {
        this.executionResult = executionResult;
    }

    public Long getVersion() {
        return this.version;
    }

    public void setVersion(final Long version) {
        this.version = version;
    }
}
