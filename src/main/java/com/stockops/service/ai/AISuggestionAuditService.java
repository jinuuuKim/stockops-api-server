package com.stockops.service.ai;

import com.stockops.entity.ai.AISuggestion;
import com.stockops.entity.ai.AISuggestionAudit;
import com.stockops.entity.ai.AISuggestionStatus;
import com.stockops.repository.ai.AISuggestionAuditRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AISuggestionAuditService {

    private static final String ACTION_CREATE = "CREATE";
    private static final String ACTION_TOOL_CREATE = "TOOL_CREATE";
    private static final String ACTION_APPROVE = "APPROVE";
    private static final String ACTION_REJECT = "REJECT";
    private static final String ACTION_EXECUTE = "EXECUTE";
    private static final String ACTION_EXECUTE_FAILED = "EXECUTE_FAILED";
    private static final String RESULT_SUCCESS = "SUCCESS";
    private static final String RESULT_FAILURE = "FAILURE";

    private final AISuggestionAuditRepository aiSuggestionAuditRepository;

    public AISuggestionAuditService(final AISuggestionAuditRepository aiSuggestionAuditRepository) {
        this.aiSuggestionAuditRepository = aiSuggestionAuditRepository;
    }

    @Transactional
    public AISuggestionAudit recordCreate(final AISuggestion suggestion,
                                          final AuditActor actor,
                                          final String requestId) {
        return recordMutation(ACTION_CREATE, suggestion, null, suggestion.getStatus(), null, summarizeSuggestion(suggestion), actor, requestId, RESULT_SUCCESS, null);
    }

    @Transactional
    public AISuggestionAudit recordToolCreatedSuggestion(final AISuggestion suggestion,
                                                         final AuditActor actor,
                                                         final String requestId) {
        return recordMutation(ACTION_TOOL_CREATE, suggestion, null, suggestion.getStatus(), null, summarizeSuggestion(suggestion), actor, requestId, RESULT_SUCCESS, null);
    }

    @Transactional
    public AISuggestionAudit recordApprove(final AISuggestion before,
                                           final AISuggestion after,
                                           final AuditActor actor,
                                           final String requestId) {
        return recordMutation(ACTION_APPROVE, before, before.getStatus(), after.getStatus(), summarizeSuggestion(before), summarizeSuggestion(after), actor, requestId, RESULT_SUCCESS, null);
    }

    @Transactional
    public AISuggestionAudit recordReject(final AISuggestion before,
                                          final AISuggestion after,
                                          final AuditActor actor,
                                          final String requestId,
                                          final String errorMessage) {
        return recordMutation(ACTION_REJECT, before, before.getStatus(), after.getStatus(), summarizeSuggestion(before), summarizeSuggestion(after), actor, requestId, RESULT_SUCCESS, errorMessage);
    }

    @Transactional
    public AISuggestionAudit recordExecute(final AISuggestion before,
                                           final AISuggestion after,
                                           final AuditActor actor,
                                           final String requestId) {
        return recordMutation(ACTION_EXECUTE, before, before.getStatus(), after.getStatus(), summarizeSuggestion(before), summarizeSuggestion(after), actor, requestId, RESULT_SUCCESS, null);
    }

    @Transactional
    public AISuggestionAudit recordFailedExecution(final AISuggestion before,
                                                   final AISuggestion after,
                                                   final AuditActor actor,
                                                   final String requestId,
                                                   final String errorMessage) {
        return recordMutation(ACTION_EXECUTE_FAILED, before, before.getStatus(), after.getStatus(), summarizeSuggestion(before), summarizeSuggestion(after), actor, requestId, RESULT_FAILURE, errorMessage);
    }

    private AISuggestionAudit recordMutation(final String action,
                                             final AISuggestion suggestion,
                                             final AISuggestionStatus previousStatus,
                                             final AISuggestionStatus nextStatus,
                                             final String beforePayloadSummary,
                                             final String afterPayloadSummary,
                                             final AuditActor actor,
                                             final String requestId,
                                             final String result,
                                             final String errorMessage) {
        Objects.requireNonNull(suggestion, "suggestion must not be null");

        final AISuggestionAudit audit = new AISuggestionAudit();
        audit.setSuggestionId(suggestion.getId());
        audit.setAction(action);
        audit.setSourceType(suggestion.getSourceType());
        audit.setApprovalMode(suggestion.getApprovalMode());
        audit.setBeforeStatus(previousStatus == null ? null : previousStatus.name());
        audit.setAfterStatus(nextStatus == null ? null : nextStatus.name());
        audit.setPreviousStatus(previousStatus == null ? null : previousStatus.name());
        audit.setNextStatus(nextStatus == null ? null : nextStatus.name());
        audit.setBeforePayloadSummary(beforePayloadSummary);
        audit.setAfterPayloadSummary(afterPayloadSummary);
        audit.setActorUserId(actor == null ? null : actor.userId());
        audit.setActorName(actor == null ? null : actor.name());
        audit.setActorRole(actor == null ? null : actor.role());
        audit.setTargetType(suggestion.getTargetType());
        audit.setTargetId(suggestion.getTargetId());
        audit.setTargetScopeType(suggestion.getTargetScopeType());
        audit.setTargetScopeId(suggestion.getTargetScopeId());
        audit.setRequestId(requestId);
        audit.setResult(result);
        audit.setErrorMessage(errorMessage);
        return aiSuggestionAuditRepository.save(audit);
    }

    private String summarizeSuggestion(final AISuggestion suggestion) {
        if (suggestion == null) {
            return null;
        }

        final String payloadJson = suggestion.getPayloadJson() == null ? "{}" : suggestion.getPayloadJson();
        return "type=" + suggestion.getType()
                + ", target=" + suggestion.getTargetType() + ':' + suggestion.getTargetId()
                + ", scope=" + suggestion.getTargetScopeType() + ':' + suggestion.getTargetScopeId()
                + ", payload=" + payloadJson;
    }

    public record AuditActor(Long userId, String name, String role) {
    }
}
