package com.stockops.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockops.entity.Role;
import com.stockops.entity.User;
import com.stockops.entity.ai.AISuggestion;
import com.stockops.entity.ai.AISuggestionStatus;
import com.stockops.exception.ConflictException;
import com.stockops.exception.ForbiddenException;
import com.stockops.exception.InvalidOperationException;
import com.stockops.exception.ResourceNotFoundException;
import com.stockops.repository.ai.AISuggestionRepository;
import com.stockops.security.AISuggestionPermissions;
import com.stockops.security.PermissionChecker;
import com.stockops.security.ScopeGuard;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AISuggestionService {

    private static final String SCOPE_CENTER = "CENTER";
    private static final String SCOPE_WAREHOUSE = "WAREHOUSE";
    private static final String SCOPE_ADMIN = "ADMIN";
    private static final String SCOPE_STORE = "STORE";
    private static final String ROLE_STORE_MANAGER = "STORE_MANAGER";
    private static final String ROLE_STORE_STAFF = "STORE_STAFF";
    private static final String ROLE_LEGACY_BRANCH_MANAGER = "BRANCH_MANAGER";
    private static final String ROLE_LEGACY_SALES_STAFF = "SALES_STAFF";
    private static final String SOURCE_TYPE_AI_AGENT = "AI_AGENT";
    private static final String EMPTY_JSON_OBJECT = "{}";
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final AISuggestionRepository aiSuggestionRepository;
    private final PermissionChecker permissionChecker;
    private final ScopeGuard scopeGuard;
    private final AISuggestionAuditService aiSuggestionAuditService;

    public AISuggestionService(final AISuggestionRepository aiSuggestionRepository,
                               final PermissionChecker permissionChecker,
                               final ScopeGuard scopeGuard,
                               final AISuggestionAuditService aiSuggestionAuditService) {
        this.aiSuggestionRepository = aiSuggestionRepository;
        this.permissionChecker = permissionChecker;
        this.scopeGuard = scopeGuard;
        this.aiSuggestionAuditService = aiSuggestionAuditService;
    }

    @Transactional
    public AISuggestion create(final CreateCommand command, final User currentUser, final String requestId) {
        assertPermission(AISuggestionPermissions.CREATE);
        final AISuggestion suggestion = command.toSuggestion();
        suggestion.setStatus(AISuggestionStatus.PENDING);
        suggestion.setCreatedByUserId(currentUser == null ? null : currentUser.getId());
        assertSuggestionScope(suggestion);

        final AISuggestion saved = aiSuggestionRepository.save(suggestion);
        if (isToolCreatedSuggestion(saved)) {
            aiSuggestionAuditService.recordToolCreatedSuggestion(saved, auditActor(currentUser), requestId);
        } else {
            aiSuggestionAuditService.recordCreate(saved, auditActor(currentUser), requestId);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<AISuggestion> list(final ListQuery query) {
        assertPermission(AISuggestionPermissions.READ);
        if (query != null && query.targetScopeId() != null) {
            assertScopeAccess(query.targetScopeType(), query.targetScopeId());
        }

        final List<AISuggestion> suggestions = loadSuggestions(query);
        validateLoadedSuggestionScopes(suggestions);
        return scopeGuard.filterByCenterWarehouseScope(
                suggestions,
                AISuggestionService::centerIdForScope,
                AISuggestionService::warehouseIdForScope);
    }

    @Transactional(readOnly = true)
    public AISuggestion detail(final Long suggestionId) {
        assertPermission(AISuggestionPermissions.READ);
        final AISuggestion suggestion = getSuggestion(suggestionId);
        assertSuggestionScope(suggestion);
        return suggestion;
    }

    @Transactional
    public AISuggestion approve(final Long suggestionId, final User currentUser, final String requestId) {
        assertPermission(AISuggestionPermissions.APPROVE);
        assertReviewerRoleAllowed(currentUser, "approve");
        final AISuggestion suggestion = getSuggestion(suggestionId);
        assertSuggestionScope(suggestion);
        assertNotExpired(suggestion);

        final AISuggestion before = copyOf(suggestion);
        transitionSuggestion(suggestion, AISuggestionStatus.APPROVED);
        suggestion.setReviewedByUserId(currentUser == null ? null : currentUser.getId());
        suggestion.setReviewedAt(Instant.now());
        suggestion.setApprovedByUserId(currentUser == null ? null : currentUser.getId());
        suggestion.setApprovedAt(Instant.now());

        final AISuggestion saved = saveWithOptimisticLockGuard(suggestion);
        aiSuggestionAuditService.recordApprove(before, saved, auditActor(currentUser), requestId);
        return saved;
    }

    @Transactional
    public AISuggestion reject(final Long suggestionId,
                               final String rejectionReason,
                               final User currentUser,
                               final String requestId) {
        assertPermission(AISuggestionPermissions.REJECT);
        final AISuggestion suggestion = getSuggestion(suggestionId);
        assertSuggestionScope(suggestion);
        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new InvalidOperationException("Rejection reason is required");
        }

        final AISuggestion before = copyOf(suggestion);
        transitionSuggestion(suggestion, AISuggestionStatus.REJECTED);
        suggestion.setReviewedByUserId(currentUser == null ? null : currentUser.getId());
        suggestion.setReviewedAt(Instant.now());
        suggestion.setRejectionReason(rejectionReason);

        final AISuggestion saved = saveWithOptimisticLockGuard(suggestion);
        aiSuggestionAuditService.recordReject(before, saved, auditActor(currentUser), requestId, rejectionReason);
        return saved;
    }

    @Transactional
    public AISuggestion execute(final Long suggestionId,
                                final String executionResult,
                                final User currentUser,
                                final String requestId) {
        assertPermission(AISuggestionPermissions.EXECUTE);
        assertReviewerRoleAllowed(currentUser, "execute");
        final AISuggestion suggestion = getSuggestion(suggestionId);
        assertSuggestionScope(suggestion);
        assertNotExpired(suggestion);

        final AISuggestion before = copyOf(suggestion);
        transitionSuggestion(suggestion, AISuggestionStatus.EXECUTED);
        suggestion.setExecutedAt(Instant.now());
        suggestion.setExecutionResult(normalizeJsonField("executionResult", executionResult));

        final AISuggestion saved = saveWithOptimisticLockGuard(suggestion);
        aiSuggestionAuditService.recordExecute(before, saved, auditActor(currentUser), requestId);
        return saved;
    }


    @Transactional
    public AISuggestion recordFailedExecution(final Long suggestionId,
                                              final String errorMessage,
                                              final User currentUser,
                                              final String requestId) {
        assertPermission(AISuggestionPermissions.EXECUTE);
        assertReviewerRoleAllowed(currentUser, "execute");
        final AISuggestion suggestion = getSuggestion(suggestionId);
        assertSuggestionScope(suggestion);

        final AISuggestion before = copyOf(suggestion);
        transitionSuggestion(suggestion, AISuggestionStatus.FAILED);
        suggestion.setExecutedAt(Instant.now());
        final String normalizedErrorMessage = errorMessage == null || errorMessage.isBlank()
                ? "Execution failed"
                : errorMessage;
        suggestion.setExecutionResult(normalizedErrorMessage);

        final AISuggestion saved = saveWithOptimisticLockGuard(suggestion);
        aiSuggestionAuditService.recordFailedExecution(before, saved, auditActor(currentUser), requestId, normalizedErrorMessage);
        return saved;
    }

    private List<AISuggestion> loadSuggestions(final ListQuery query) {
        if (query == null) {
            return aiSuggestionRepository.findAll();
        }
        if (query.targetScopeType() != null && query.targetScopeId() != null) {
            return aiSuggestionRepository
                    .findByTargetScopeTypeAndTargetScopeIdOrderByIdAsc(
                            normalizeSupportedScopeType("targetScopeType", query.targetScopeType()),
                            query.targetScopeId())
                    .stream()
                    .filter(suggestion -> query.status() == null || suggestion.getStatus() == query.status())
                    .toList();
        }
        if (query.targetScopeType() != null) {
            return aiSuggestionRepository
                    .findByTargetScopeTypeOrderByIdAsc(
                            normalizeSupportedScopeType("targetScopeType", query.targetScopeType()))
                    .stream()
                    .filter(suggestion -> query.status() == null || suggestion.getStatus() == query.status())
                    .toList();
        }
        if (query.status() != null) {
            return aiSuggestionRepository.findByStatusOrderByIdAsc(query.status());
        }
        return aiSuggestionRepository.findAll();
    }

    private AISuggestion getSuggestion(final Long suggestionId) {
        return aiSuggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new ResourceNotFoundException("AI suggestion not found: " + suggestionId));
    }

    private void validateLoadedSuggestionScopes(final List<AISuggestion> suggestions) {
        for (AISuggestion suggestion : suggestions) {
            normalizeSupportedScopeType("targetScopeType", suggestion.getTargetScopeType());
            if (suggestion.getTargetScopeId() == null) {
                throw new InvalidOperationException(suggestion.getTargetScopeType() + " scope requires targetScopeId");
            }
            if (suggestion.getRequestedScopeType() != null) {
                normalizeSupportedScopeType("requestedScopeType", suggestion.getRequestedScopeType());
            }
        }
    }

    private AISuggestion saveWithOptimisticLockGuard(final AISuggestion suggestion) {
        try {
            return aiSuggestionRepository.save(suggestion);
        } catch (OptimisticLockingFailureException exception) {
            throw new ConflictException("AI suggestion was modified by another request");
        }
    }

    private void transitionSuggestion(final AISuggestion suggestion, final AISuggestionStatus nextStatus) {
        try {
            suggestion.transitionTo(nextStatus);
        } catch (IllegalStateException exception) {
            throw new InvalidOperationException(exception.getMessage());
        }
    }

    private void assertPermission(final String permission) {
        if (!permissionChecker.hasPermission(permission)) {
            throw new ForbiddenException("Missing permission: " + permission);
        }
    }

    private void assertSuggestionScope(final AISuggestion suggestion) {
        assertScopeAccess(suggestion.getTargetScopeType(), suggestion.getTargetScopeId());
    }

    private void assertScopeAccess(final String scopeType, final Long scopeId) {
        final String normalizedScopeType = normalizeSupportedScopeType("targetScopeType", scopeType);
        if (scopeId == null) {
            throw new InvalidOperationException(normalizedScopeType + " scope requires targetScopeId");
        }
        if (SCOPE_ADMIN.equals(normalizedScopeType)) {
            scopeGuard.assertAdminAccess();
            return;
        }
        if (SCOPE_CENTER.equals(normalizedScopeType)) {
            scopeGuard.assertCenterAccess(scopeId);
            return;
        }
        if (SCOPE_WAREHOUSE.equals(normalizedScopeType)) {
            scopeGuard.assertWarehouseAccess(scopeId);
            return;
        }
        if (SCOPE_STORE.equals(normalizedScopeType)) {
            scopeGuard.assertStoreAccess(scopeId);
            return;
        }
        throw new InvalidOperationException("Unsupported AI suggestion targetScopeType: " + scopeType);
    }

    private void assertReviewerRoleAllowed(final User currentUser, final String action) {
        final String roleName = roleName(currentUser);
        final String effectiveRoleName = effectiveRoleName(roleName);
        if (ROLE_STORE_STAFF.equals(effectiveRoleName)) {
            throw new ForbiddenException(
                    "Role cannot " + action + " AI suggestions: " + roleName + " (effective role: " + effectiveRoleName + ")");
        }
    }

    private void assertNotExpired(final AISuggestion suggestion) {
        if (suggestion.getExpiresAt() != null && !suggestion.getExpiresAt().isAfter(Instant.now())) {
            throw new InvalidOperationException("Expired AI suggestions cannot be approved or executed");
        }
    }

    private static Long centerIdForScope(final AISuggestion suggestion) {
        return SCOPE_CENTER.equals(normalizeScopeType(suggestion.getTargetScopeType())) ? suggestion.getTargetScopeId() : null;
    }

    private static Long warehouseIdForScope(final AISuggestion suggestion) {
        final String scopeType = normalizeScopeType(suggestion.getTargetScopeType());
        return SCOPE_WAREHOUSE.equals(scopeType) || SCOPE_STORE.equals(scopeType) ? suggestion.getTargetScopeId() : null;
    }

    private static String normalizeScopeType(final String scopeType) {
        return scopeType == null ? null : scopeType.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeSupportedScopeType(final String fieldName, final String scopeType) {
        final String normalizedScopeType = normalizeScopeType(scopeType);
        if (normalizedScopeType == null || normalizedScopeType.isBlank()) {
            throw new InvalidOperationException(fieldName + " is required");
        }
        if (SCOPE_ADMIN.equals(normalizedScopeType)
                || SCOPE_CENTER.equals(normalizedScopeType)
                || SCOPE_WAREHOUSE.equals(normalizedScopeType)
                || SCOPE_STORE.equals(normalizedScopeType)) {
            return normalizedScopeType;
        }
        throw new InvalidOperationException("Unsupported AI suggestion " + fieldName + ": " + scopeType);
    }

    private boolean isToolCreatedSuggestion(final AISuggestion suggestion) {
        return SOURCE_TYPE_AI_AGENT.equals(normalizeScopeType(suggestion.getSourceType()));
    }

    private static String normalizeJsonField(final String fieldName, final String value) {
        if (value == null || value.isBlank()) {
            return EMPTY_JSON_OBJECT;
        }
        try {
            JSON_MAPPER.readTree(value);
            return value;
        } catch (JsonProcessingException exception) {
            throw new InvalidOperationException(fieldName + " must contain valid JSON");
        }
    }

    private static String toJsonString(final String value) {
        try {
            return JSON_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new InvalidOperationException("executionResult must contain valid JSON");
        }
    }

    private AISuggestionAuditService.AuditActor auditActor(final User user) {
        return new AISuggestionAuditService.AuditActor(
                user == null ? null : user.getId(),
                user == null ? null : user.getName(),
                roleName(user));
    }

    private String roleName(final User user) {
        final Role role = user == null ? null : user.getRole();
        return role == null || role.getName() == null ? null : role.getName().trim().toUpperCase(Locale.ROOT);
    }

    private static String effectiveRoleName(final String roleName) {
        if (ROLE_LEGACY_BRANCH_MANAGER.equals(roleName)) {
            return ROLE_STORE_MANAGER;
        }
        if (ROLE_LEGACY_SALES_STAFF.equals(roleName)) {
            return ROLE_STORE_STAFF;
        }
        return roleName;
    }

    private AISuggestion copyOf(final AISuggestion source) {
        final AISuggestion copy = new AISuggestion();
        copy.setId(source.getId());
        copy.setType(source.getType());
        copy.setSeverity(source.getSeverity());
        copy.setTitle(source.getTitle());
        copy.setSummary(source.getSummary());
        copy.setReason(source.getReason());
        copy.setRecommendedAction(source.getRecommendedAction());
        copy.setTargetType(source.getTargetType());
        copy.setTargetId(source.getTargetId());
        copy.setTargetScopeType(source.getTargetScopeType());
        copy.setTargetScopeId(source.getTargetScopeId());
        copy.setPayloadJson(source.getPayloadJson());
        copy.setConfidenceScore(source.getConfidenceScore());
        copy.setSource(source.getSource());
        copy.setSourceType(source.getSourceType());
        copy.setCreatedByUserId(source.getCreatedByUserId());
        copy.setCreatedFromApp(source.getCreatedFromApp());
        copy.setForecastSourceType(source.getForecastSourceType());
        copy.setForecastSourceId(source.getForecastSourceId());
        copy.setForecastModelVersion(source.getForecastModelVersion());
        copy.setForecastGeneratedAt(source.getForecastGeneratedAt());
        copy.setForecastSourcePayloadJson(source.getForecastSourcePayloadJson());
        copy.setStatus(source.getStatus());
        copy.setVisibleToApp(source.getVisibleToApp());
        copy.setApprovalMode(source.getApprovalMode());
        copy.setRequestedOnBehalfUserId(source.getRequestedOnBehalfUserId());
        copy.setRequestedScopeType(source.getRequestedScopeType());
        copy.setRequestedScopeId(source.getRequestedScopeId());
        copy.setExpiresAt(source.getExpiresAt());
        copy.setReviewedByUserId(source.getReviewedByUserId());
        copy.setReviewedAt(source.getReviewedAt());
        copy.setApprovedByUserId(source.getApprovedByUserId());
        copy.setApprovedAt(source.getApprovedAt());
        copy.setExecutedAt(source.getExecutedAt());
        copy.setRejectionReason(source.getRejectionReason());
        copy.setExecutionResult(source.getExecutionResult());
        copy.setVersion(source.getVersion());
        return copy;
    }

    public record ListQuery(AISuggestionStatus status, String targetScopeType, Long targetScopeId) {
    }

    public record CreateCommand(
            String type,
            String severity,
            String title,
            String summary,
            String reason,
            String recommendedAction,
            String targetType,
            Long targetId,
            String targetScopeType,
            Long targetScopeId,
            String payloadJson,
            Double confidenceScore,
            String source,
            String sourceType,
            String createdFromApp,
            String forecastSourceType,
            Long forecastSourceId,
            String forecastModelVersion,
            Instant forecastGeneratedAt,
            String forecastSourcePayloadJson,
            String visibleToApp,
            String approvalMode,
            Long requestedOnBehalfUserId,
            String requestedScopeType,
            Long requestedScopeId,
            Instant expiresAt) {

        private AISuggestion toSuggestion() {
            final AISuggestion suggestion = new AISuggestion();
            suggestion.setType(type);
            suggestion.setSeverity(severity);
            suggestion.setTitle(title);
            suggestion.setSummary(summary);
            suggestion.setReason(reason);
            suggestion.setRecommendedAction(recommendedAction);
            suggestion.setTargetType(targetType);
            suggestion.setTargetId(targetId);
            suggestion.setTargetScopeType(normalizeSupportedScopeType("targetScopeType", targetScopeType));
            suggestion.setTargetScopeId(targetScopeId);
            suggestion.setPayloadJson(normalizeJsonField("payloadJson", payloadJson));
            suggestion.setConfidenceScore(confidenceScore);
            suggestion.setSource(source);
            suggestion.setSourceType(sourceType);
            suggestion.setCreatedFromApp(createdFromApp);
            suggestion.setForecastSourceType(forecastSourceType);
            suggestion.setForecastSourceId(forecastSourceId);
            suggestion.setForecastModelVersion(forecastModelVersion);
            suggestion.setForecastGeneratedAt(forecastGeneratedAt);
            suggestion.setForecastSourcePayloadJson(normalizeJsonField("forecastSourcePayloadJson", forecastSourcePayloadJson));
            suggestion.setVisibleToApp(visibleToApp);
            suggestion.setApprovalMode(approvalMode);
            suggestion.setRequestedOnBehalfUserId(requestedOnBehalfUserId);
            suggestion.setRequestedScopeType(requestedScopeType == null
                    ? null
                    : normalizeSupportedScopeType("requestedScopeType", requestedScopeType));
            suggestion.setRequestedScopeId(requestedScopeId);
            suggestion.setExpiresAt(expiresAt);
            return suggestion;
        }
    }
}
