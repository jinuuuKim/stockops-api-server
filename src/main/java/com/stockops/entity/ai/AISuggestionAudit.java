package com.stockops.entity.ai;

import com.stockops.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(schema = "analytics", name = "ai_suggestion_audits")
public class AISuggestionAudit extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "suggestion_id", nullable = false)
    private Long suggestionId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "source_type", nullable = false, length = 100)
    private String sourceType;

    @Column(name = "approval_mode", nullable = false, length = 100)
    private String approvalMode;

    @Column(name = "before_status", length = 50)
    private String beforeStatus;

    @Column(name = "after_status", length = 50)
    private String afterStatus;

    @Column(name = "previous_status", length = 50)
    private String previousStatus;

    @Column(name = "next_status", length = 50)
    private String nextStatus;

    @Column(name = "before_payload_summary", columnDefinition = "TEXT")
    private String beforePayloadSummary;

    @Column(name = "after_payload_summary", columnDefinition = "TEXT")
    private String afterPayloadSummary;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_name", length = 200)
    private String actorName;

    @Column(name = "actor_role", length = 100)
    private String actorRole;

    @Column(name = "target_type", length = 100)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "target_scope_type", length = 50)
    private String targetScopeType;

    @Column(name = "target_scope_id")
    private Long targetScopeId;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "result", nullable = false, length = 50)
    private String result;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    public AISuggestionAudit() {
    }

    @PrePersist
    protected void onCreateAudit() {
        if (this.recordedAt == null) {
            this.recordedAt = Instant.now();
        }
    }

    public Long getId() {
        return this.id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public Long getSuggestionId() {
        return this.suggestionId;
    }

    public void setSuggestionId(final Long suggestionId) {
        this.suggestionId = suggestionId;
    }

    public String getAction() {
        return this.action;
    }

    public void setAction(final String action) {
        this.action = action;
    }

    public String getSourceType() {
        return this.sourceType;
    }

    public void setSourceType(final String sourceType) {
        this.sourceType = sourceType;
    }

    public String getApprovalMode() {
        return this.approvalMode;
    }

    public void setApprovalMode(final String approvalMode) {
        this.approvalMode = approvalMode;
    }

    public String getBeforeStatus() {
        return this.beforeStatus;
    }

    public void setBeforeStatus(final String beforeStatus) {
        this.beforeStatus = beforeStatus;
    }

    public String getAfterStatus() {
        return this.afterStatus;
    }

    public void setAfterStatus(final String afterStatus) {
        this.afterStatus = afterStatus;
    }

    public String getPreviousStatus() {
        return this.previousStatus;
    }

    public void setPreviousStatus(final String previousStatus) {
        this.previousStatus = previousStatus;
    }

    public String getNextStatus() {
        return this.nextStatus;
    }

    public void setNextStatus(final String nextStatus) {
        this.nextStatus = nextStatus;
    }

    public String getBeforePayloadSummary() {
        return this.beforePayloadSummary;
    }

    public void setBeforePayloadSummary(final String beforePayloadSummary) {
        this.beforePayloadSummary = beforePayloadSummary;
    }

    public String getAfterPayloadSummary() {
        return this.afterPayloadSummary;
    }

    public void setAfterPayloadSummary(final String afterPayloadSummary) {
        this.afterPayloadSummary = afterPayloadSummary;
    }

    public Long getActorUserId() {
        return this.actorUserId;
    }

    public void setActorUserId(final Long actorUserId) {
        this.actorUserId = actorUserId;
    }

    public String getActorName() {
        return this.actorName;
    }

    public void setActorName(final String actorName) {
        this.actorName = actorName;
    }

    public String getActorRole() {
        return this.actorRole;
    }

    public void setActorRole(final String actorRole) {
        this.actorRole = actorRole;
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

    public String getRequestId() {
        return this.requestId;
    }

    public void setRequestId(final String requestId) {
        this.requestId = requestId;
    }

    public String getResult() {
        return this.result;
    }

    public void setResult(final String result) {
        this.result = result;
    }

    public String getErrorMessage() {
        return this.errorMessage;
    }

    public void setErrorMessage(final String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getRecordedAt() {
        return this.recordedAt;
    }

    public void setRecordedAt(final Instant recordedAt) {
        this.recordedAt = recordedAt;
    }
}
