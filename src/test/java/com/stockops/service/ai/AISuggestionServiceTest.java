package com.stockops.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockops.entity.Role;
import com.stockops.entity.User;
import com.stockops.entity.ai.AISuggestion;
import com.stockops.entity.ai.AISuggestionStatus;
import com.stockops.exception.ConflictException;
import com.stockops.exception.ForbiddenException;
import com.stockops.exception.InvalidOperationException;
import com.stockops.repository.ai.AISuggestionRepository;
import com.stockops.security.AISuggestionPermissions;
import com.stockops.security.PermissionChecker;
import com.stockops.security.ScopeGuard;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class AISuggestionServiceTest {

    @Mock
    private AISuggestionRepository aiSuggestionRepository;

    @Mock
    private PermissionChecker permissionChecker;

    @Mock
    private ScopeGuard scopeGuard;

    @Mock
    private AISuggestionAuditService aiSuggestionAuditService;

    private AISuggestionService aiSuggestionService;

    @BeforeEach
    void setUp() {
        aiSuggestionService = new AISuggestionService(
                aiSuggestionRepository,
                permissionChecker,
                scopeGuard,
                aiSuggestionAuditService);
    }

    @Test
    void createPersistsPendingToolSuggestionWithScopeAndAudit() {
        final User creator = user(7L, "MANAGER");
        when(permissionChecker.hasPermission(AISuggestionPermissions.CREATE)).thenReturn(true);
        when(aiSuggestionRepository.save(any(AISuggestion.class))).thenAnswer(invocation -> {
            final AISuggestion suggestion = invocation.getArgument(0);
            suggestion.setId(1L);
            return suggestion;
        });

        final AISuggestion saved = aiSuggestionService.create(createCommand(), creator, "req-create");

        assertThat(saved.getStatus()).isEqualTo(AISuggestionStatus.PENDING);
        assertThat(saved.getCreatedByUserId()).isEqualTo(7L);
        assertThat(saved.getTargetScopeType()).isEqualTo("WAREHOUSE");
        verify(scopeGuard).assertWarehouseAccess(456L);
        verify(aiSuggestionAuditService).recordToolCreatedSuggestion(
                eq(saved),
                eq(new AISuggestionAuditService.AuditActor(7L, "User 7", "MANAGER")),
                eq("req-create"));
    }

    @Test
    void createPersistsReviewPayloadAndDefaultsMissingOptionalJson() {
        final User creator = user(8L, "ADMIN");
        when(permissionChecker.hasPermission(AISuggestionPermissions.CREATE)).thenReturn(true);
        when(aiSuggestionRepository.save(any(AISuggestion.class))).thenAnswer(invocation -> {
            final AISuggestion suggestion = invocation.getArgument(0);
            suggestion.setId(2L);
            return suggestion;
        });

        final AISuggestion saved = aiSuggestionService.create(reviewPayloadCreateCommand(), creator, "req-review-payload");

        assertThat(saved.getType()).isEqualTo("INVENTORY_REPLENISHMENT");
        assertThat(saved.getPayloadJson()).isEqualTo("{\"qa\":true}");
        assertThat(saved.getForecastSourcePayloadJson()).isEqualTo("{}");
        assertThat(saved.getExecutionResult()).isEqualTo("{}");
        assertThat(saved.getTargetScopeType()).isEqualTo("CENTER");
        verify(scopeGuard).assertCenterAccess(1L);
        verify(aiSuggestionAuditService).recordCreate(
                eq(saved),
                eq(new AISuggestionAuditService.AuditActor(8L, "User 8", "ADMIN")),
                eq("req-review-payload"));
    }

    @Test
    void createRejectsMalformedPayloadJsonBeforeSave() {
        when(permissionChecker.hasPermission(AISuggestionPermissions.CREATE)).thenReturn(true);

        assertThatThrownBy(() -> aiSuggestionService.create(malformedPayloadCreateCommand(), user(9L, "ADMIN"), "req-bad-json"))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("payloadJson must contain valid JSON");

        verify(aiSuggestionRepository, never()).save(any(AISuggestion.class));
    }

    @Test
    void createPersistsStoreScopedSuggestion() {
        final User creator = user(10L, "STORE_MANAGER");
        when(permissionChecker.hasPermission(AISuggestionPermissions.CREATE)).thenReturn(true);
        when(aiSuggestionRepository.save(any(AISuggestion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final AISuggestion saved = aiSuggestionService.create(storeCreateCommand(), creator, "req-store-create");

        assertThat(saved.getTargetScopeType()).isEqualTo("STORE");
        assertThat(saved.getRequestedScopeType()).isEqualTo("STORE");
        verify(scopeGuard).assertStoreAccess(456L);
    }

    @Test
    void createPersistsAdminScopedSuggestionOnlyAfterAdminScopeGuard() {
        final User creator = user(12L, "ADMIN");
        when(permissionChecker.hasPermission(AISuggestionPermissions.CREATE)).thenReturn(true);
        when(aiSuggestionRepository.save(any(AISuggestion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final AISuggestion saved = aiSuggestionService.create(adminCreateCommand(), creator, "req-admin-create");

        assertThat(saved.getTargetScopeType()).isEqualTo("ADMIN");
        verify(scopeGuard).assertAdminAccess();
    }

    @Test
    void createRejectsLegacyBranchScopeBeforeSave() {
        when(permissionChecker.hasPermission(AISuggestionPermissions.CREATE)).thenReturn(true);

        assertThatThrownBy(() -> aiSuggestionService.create(legacyBranchScopeCreateCommand(), user(13L, "ADMIN"), "req-branch"))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Unsupported AI suggestion targetScopeType: BRANCH");

        verify(aiSuggestionRepository, never()).save(any(AISuggestion.class));
    }

    @Test
    void createRejectsLegacyRequestedBranchScopeBeforeSave() {
        when(permissionChecker.hasPermission(AISuggestionPermissions.CREATE)).thenReturn(true);

        assertThatThrownBy(() -> aiSuggestionService.create(legacyRequestedScopeCreateCommand(), user(14L, "ADMIN"), "req-requested-branch"))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Unsupported AI suggestion requestedScopeType: BRANCH");

        verify(aiSuggestionRepository, never()).save(any(AISuggestion.class));
    }

    @Test
    void listRequiresReadPermission() {
        when(permissionChecker.hasPermission(AISuggestionPermissions.READ)).thenReturn(false);

        assertThatThrownBy(() -> aiSuggestionService.list(new AISuggestionService.ListQuery(null, null, null)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining(AISuggestionPermissions.READ);

        verify(aiSuggestionRepository, never()).findAll();
    }

    @Test
    void listWithStoreQueryRequiresStoreScopeAndFiltersRows() {
        final AISuggestion storeSuggestion = suggestion(AISuggestionStatus.PENDING);
        storeSuggestion.setTargetScopeType("STORE");
        when(permissionChecker.hasPermission(AISuggestionPermissions.READ)).thenReturn(true);
        when(aiSuggestionRepository.findByTargetScopeTypeAndTargetScopeIdOrderByIdAsc("STORE", 456L))
                .thenReturn(List.of(storeSuggestion));
        when(scopeGuard.filterByCenterWarehouseScope(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        final List<AISuggestion> suggestions = aiSuggestionService.list(
                new AISuggestionService.ListQuery(AISuggestionStatus.PENDING, "store", 456L));

        assertThat(suggestions).containsExactly(storeSuggestion);
        verify(scopeGuard).assertStoreAccess(456L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "CENTER", "WAREHOUSE", "STORE"})
    void listWithScopeTypeOnlyFiltersRowsWithoutRequiringScopeId(final String scopeType) {
        final AISuggestion scopeSuggestion = suggestion(AISuggestionStatus.PENDING);
        scopeSuggestion.setTargetScopeType(scopeType);
        scopeSuggestion.setTargetScopeId(scopeIdFor(scopeType));
        when(permissionChecker.hasPermission(AISuggestionPermissions.READ)).thenReturn(true);
        when(aiSuggestionRepository.findByTargetScopeTypeOrderByIdAsc(scopeType)).thenReturn(List.of(scopeSuggestion));
        when(scopeGuard.filterByCenterWarehouseScope(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        final List<AISuggestion> suggestions = aiSuggestionService.list(
                new AISuggestionService.ListQuery(AISuggestionStatus.PENDING, scopeType.toLowerCase(), null));

        assertThat(suggestions).containsExactly(scopeSuggestion);
        verify(aiSuggestionRepository).findByTargetScopeTypeOrderByIdAsc(scopeType);
        verify(scopeGuard, never()).assertAdminAccess();
        verify(scopeGuard, never()).assertCenterAccess(any());
        verify(scopeGuard, never()).assertWarehouseAccess(any());
        verify(scopeGuard, never()).assertStoreAccess(any());
    }

    @Test
    void listRejectsPersistedLegacyBranchScopeRows() {
        final AISuggestion legacySuggestion = suggestion(AISuggestionStatus.PENDING);
        legacySuggestion.setTargetScopeType("BRANCH");
        when(permissionChecker.hasPermission(AISuggestionPermissions.READ)).thenReturn(true);
        when(aiSuggestionRepository.findAll()).thenReturn(List.of(legacySuggestion));

        assertThatThrownBy(() -> aiSuggestionService.list(new AISuggestionService.ListQuery(null, null, null)))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Unsupported AI suggestion targetScopeType: BRANCH");

        verify(scopeGuard, never()).filterByCenterWarehouseScope(any(), any(), any());
    }

    @Test
    void detailRejectsOutOfScopeSuggestion() {
        final AISuggestion suggestion = suggestion(AISuggestionStatus.PENDING);
        when(permissionChecker.hasPermission(AISuggestionPermissions.READ)).thenReturn(true);
        when(aiSuggestionRepository.findById(1L)).thenReturn(Optional.of(suggestion));
        doThrow(new ForbiddenException("Access denied for warehouse: 456"))
                .when(scopeGuard).assertWarehouseAccess(456L);

        assertThatThrownBy(() -> aiSuggestionService.detail(1L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("warehouse");
    }

    @Test
    void approveAndExecuteHappyPathTransitionsAndAudits() {
        final User manager = user(11L, "STORE_MANAGER");
        final AISuggestion pending = suggestion(AISuggestionStatus.PENDING);
        final AISuggestion approved = suggestion(AISuggestionStatus.APPROVED);
        when(permissionChecker.hasPermission(AISuggestionPermissions.APPROVE)).thenReturn(true);
        when(permissionChecker.hasPermission(AISuggestionPermissions.EXECUTE)).thenReturn(true);
        when(aiSuggestionRepository.findById(1L)).thenReturn(Optional.of(pending), Optional.of(approved));
        when(aiSuggestionRepository.save(any(AISuggestion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final AISuggestion approvedResult = aiSuggestionService.approve(1L, manager, "req-approve");
        final AISuggestion executedResult = aiSuggestionService.execute(1L, "{\"purchaseOrderId\":99}", manager, "req-execute");

        assertThat(approvedResult.getStatus()).isEqualTo(AISuggestionStatus.APPROVED);
        assertThat(approvedResult.getApprovedByUserId()).isEqualTo(11L);
        assertThat(executedResult.getStatus()).isEqualTo(AISuggestionStatus.EXECUTED);
        assertThat(executedResult.getExecutionResult()).isEqualTo("{\"purchaseOrderId\":99}");
        verify(scopeGuard, times(2)).assertWarehouseAccess(456L);
        verify(aiSuggestionAuditService).recordApprove(any(AISuggestion.class), eq(approvedResult), any(), eq("req-approve"));
        verify(aiSuggestionAuditService).recordExecute(any(AISuggestion.class), eq(executedResult), any(), eq("req-execute"));
    }

    @Test
    void recordFailedExecutionTransitionsApprovedSuggestionAndAuditsFailure() {
        final User manager = user(12L, "MANAGER");
        final AISuggestion approved = suggestion(AISuggestionStatus.APPROVED);
        when(permissionChecker.hasPermission(AISuggestionPermissions.EXECUTE)).thenReturn(true);
        when(aiSuggestionRepository.findById(1L)).thenReturn(Optional.of(approved));
        when(aiSuggestionRepository.save(any(AISuggestion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final AISuggestion failed = aiSuggestionService.recordFailedExecution(
                1L,
                "inventory service unavailable",
                manager,
                "req-execute-failed");

        assertThat(failed.getStatus()).isEqualTo(AISuggestionStatus.FAILED);
        assertThat(failed.getExecutionResult()).isEqualTo("inventory service unavailable");
        assertThat(failed.getExecutedAt()).isNotNull();
        verify(aiSuggestionAuditService).recordFailedExecution(
                any(AISuggestion.class),
                eq(failed),
                eq(new AISuggestionAuditService.AuditActor(12L, "User 12", "MANAGER")),
                eq("req-execute-failed"),
                eq("inventory service unavailable"));
    }

    @Test
    void legacyBranchManagerCanApproveAsStoreManagerAlias() {
        final AISuggestion pending = suggestion(AISuggestionStatus.PENDING);
        when(permissionChecker.hasPermission(AISuggestionPermissions.APPROVE)).thenReturn(true);
        when(aiSuggestionRepository.findById(1L)).thenReturn(Optional.of(pending));
        when(aiSuggestionRepository.save(any(AISuggestion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final AISuggestion approved = aiSuggestionService.approve(1L, user(21L, "BRANCH_MANAGER"), "req-approve");

        assertThat(approved.getStatus()).isEqualTo(AISuggestionStatus.APPROVED);
        verify(scopeGuard).assertWarehouseAccess(456L);
    }

    @Test
    void storeStaffCannotExecuteEvenWithPermission() {
        when(permissionChecker.hasPermission(AISuggestionPermissions.EXECUTE)).thenReturn(true);

        assertThatThrownBy(() -> aiSuggestionService.execute(1L, "{}", user(22L, "STORE_STAFF"), "req-execute"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("STORE_STAFF");

        verify(aiSuggestionRepository, never()).findById(1L);
    }

    @Test
    void legacySalesStaffCannotExecuteAsStoreStaffAlias() {
        when(permissionChecker.hasPermission(AISuggestionPermissions.EXECUTE)).thenReturn(true);

        assertThatThrownBy(() -> aiSuggestionService.execute(1L, "{}", user(23L, "SALES_STAFF"), "req-execute"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("SALES_STAFF")
                .hasMessageContaining("STORE_STAFF");

        verify(aiSuggestionRepository, never()).findById(1L);
    }

    @Test
    void rejectRequiresPermissionAndReason() {
        final AISuggestion pending = suggestion(AISuggestionStatus.PENDING);
        when(permissionChecker.hasPermission(AISuggestionPermissions.REJECT)).thenReturn(true);
        when(aiSuggestionRepository.findById(1L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> aiSuggestionService.reject(1L, " ", user(31L, "MANAGER"), "req-reject"))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Rejection reason");
    }


    @Test
    void invalidWorkflowTransitionThrowsInvalidOperationException() {
        final AISuggestion pending = suggestion(AISuggestionStatus.PENDING);
        when(permissionChecker.hasPermission(AISuggestionPermissions.EXECUTE)).thenReturn(true);
        when(aiSuggestionRepository.findById(1L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> aiSuggestionService.execute(1L, "{}", user(32L, "MANAGER"), "req-execute-pending"))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("PENDING to EXECUTED");
    }

    @Test
    void executeRejectsMalformedExecutionResultBeforeSave() {
        final AISuggestion approved = suggestion(AISuggestionStatus.APPROVED);
        when(permissionChecker.hasPermission(AISuggestionPermissions.EXECUTE)).thenReturn(true);
        when(aiSuggestionRepository.findById(1L)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> aiSuggestionService.execute(1L, "{\"ok\":", user(33L, "MANAGER"), "req-bad-exec-json"))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("executionResult must contain valid JSON");

        verify(aiSuggestionRepository, never()).save(any(AISuggestion.class));
    }

    @Test
    void executeRejectsStaleSuggestionBeforeMutating() {
        final AISuggestion approved = suggestion(AISuggestionStatus.APPROVED);
        approved.setExpiresAt(Instant.now().minusSeconds(60));
        when(permissionChecker.hasPermission(AISuggestionPermissions.EXECUTE)).thenReturn(true);
        when(aiSuggestionRepository.findById(1L)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> aiSuggestionService.execute(1L, "{}", user(41L, "MANAGER"), "req-execute"))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Expired");
        assertThat(approved.getStatus()).isEqualTo(AISuggestionStatus.APPROVED);
        verify(aiSuggestionRepository, never()).save(any(AISuggestion.class));
    }

    @Test
    void concurrentExecutionProducesOneSuccessAndOneSafeFailure() {
        final User manager = user(51L, "MANAGER");
        when(permissionChecker.hasPermission(AISuggestionPermissions.EXECUTE)).thenReturn(true);
        when(aiSuggestionRepository.findById(1L)).thenReturn(
                Optional.of(suggestion(AISuggestionStatus.APPROVED)),
                Optional.of(suggestion(AISuggestionStatus.APPROVED)));
        when(aiSuggestionRepository.save(any(AISuggestion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0))
                .thenThrow(new ObjectOptimisticLockingFailureException(AISuggestion.class, 1L));

        final AISuggestion firstResult = aiSuggestionService.execute(1L, "{}", manager, "req-execute-1");
        assertThat(firstResult.getStatus()).isEqualTo(AISuggestionStatus.EXECUTED);

        assertThatThrownBy(() -> aiSuggestionService.execute(1L, "{}", manager, "req-execute-2"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("modified by another request");
    }

    @Test
    void listFiltersOutRowsOutsideCurrentScope() {
        final AISuggestion warehouseSuggestion = suggestion(AISuggestionStatus.PENDING);
        final AISuggestion centerSuggestion = suggestion(AISuggestionStatus.APPROVED);
        centerSuggestion.setId(2L);
        centerSuggestion.setTargetScopeType("CENTER");
        centerSuggestion.setTargetScopeId(100L);
        when(permissionChecker.hasPermission(AISuggestionPermissions.READ)).thenReturn(true);
        when(aiSuggestionRepository.findAll()).thenReturn(List.of(warehouseSuggestion, centerSuggestion));
        when(scopeGuard.filterByCenterWarehouseScope(any(), any(), any())).thenReturn(List.of(centerSuggestion));

        final List<AISuggestion> suggestions = aiSuggestionService.list(new AISuggestionService.ListQuery(null, null, null));

        assertThat(suggestions).containsExactly(centerSuggestion);
    }

    private AISuggestionService.CreateCommand createCommand() {
        return scopedCreateCommand("warehouse", 456L, "WAREHOUSE", 456L);
    }

    private AISuggestionService.CreateCommand storeCreateCommand() {
        return scopedCreateCommand("store", 456L, "STORE", 456L);
    }

    private AISuggestionService.CreateCommand adminCreateCommand() {
        return scopedCreateCommand("admin", 0L, "ADMIN", 0L);
    }

    private AISuggestionService.CreateCommand legacyBranchScopeCreateCommand() {
        return scopedCreateCommand("BRANCH", 456L, "STORE", 456L);
    }

    private AISuggestionService.CreateCommand legacyRequestedScopeCreateCommand() {
        return scopedCreateCommand("STORE", 456L, "BRANCH", 456L);
    }

    private AISuggestionService.CreateCommand scopedCreateCommand(final String targetScopeType,
                                                                 final Long targetScopeId,
                                                                 final String requestedScopeType,
                                                                 final Long requestedScopeId) {
        return new AISuggestionService.CreateCommand(
                "PURCHASE_ORDER_CREATE",
                "HIGH",
                "Restock milk",
                "Milk forecast is below threshold",
                "Expected demand exceeds current stock",
                "Create draft purchase order",
                "PRODUCT",
                123L,
                targetScopeType,
                targetScopeId,
                "{\"quantity\":100}",
                0.92D,
                "planner",
                "AI_AGENT",
                "admin-web",
                "AI_RECOMMENDATION",
                77L,
                "statistical-v1",
                Instant.now(),
                "{}",
                "BOTH",
                "MANUAL_APPROVAL_REQUIRED",
                null,
                requestedScopeType,
                requestedScopeId,
                Instant.now().plusSeconds(3600));
    }

    private AISuggestionService.CreateCommand reviewPayloadCreateCommand() {
        return new AISuggestionService.CreateCommand(
                "INVENTORY_REPLENISHMENT",
                "HIGH",
                "Codex QA create debug",
                "QA test suggestion",
                "Debug creation",
                "No business write",
                "PRODUCT",
                1L,
                "CENTER",
                1L,
                "{\"qa\":true}",
                0.87D,
                "QA",
                "USER_REQUEST",
                "admin-web",
                null,
                null,
                null,
                null,
                null,
                "ADMIN_WEB",
                "MANUAL",
                null,
                "CENTER",
                1L,
                null);
    }

    private AISuggestionService.CreateCommand malformedPayloadCreateCommand() {
        return new AISuggestionService.CreateCommand(
                "INVENTORY_REPLENISHMENT",
                "HIGH",
                "Codex QA create debug",
                "QA test suggestion",
                "Debug creation",
                "No business write",
                "PRODUCT",
                1L,
                "CENTER",
                1L,
                "{\"qa\":",
                0.87D,
                "QA",
                "USER_REQUEST",
                "admin-web",
                null,
                null,
                null,
                null,
                null,
                "ADMIN_WEB",
                "MANUAL",
                null,
                "CENTER",
                1L,
                null);
    }

    private AISuggestion suggestion(final AISuggestionStatus status) {
        final AISuggestion suggestion = new AISuggestion();
        suggestion.setId(1L);
        suggestion.setType("PURCHASE_ORDER_CREATE");
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
        suggestion.setSourceType("AI_AGENT");
        suggestion.setVisibleToApp("BOTH");
        suggestion.setApprovalMode("MANUAL_APPROVAL_REQUIRED");
        suggestion.setStatus(status);
        suggestion.setExpiresAt(Instant.now().plusSeconds(3600));
        suggestion.setVersion(0L);
        return suggestion;
    }

    private Long scopeIdFor(final String scopeType) {
        return switch (scopeType) {
            case "ADMIN" -> 0L;
            case "CENTER" -> 100L;
            case "WAREHOUSE" -> 456L;
            case "STORE" -> 789L;
            default -> throw new IllegalArgumentException("Unsupported scope type: " + scopeType);
        };
    }

    private User user(final Long id, final String roleName) {
        final Role role = new Role();
        role.setName(roleName);

        final User user = new User();
        user.setId(id);
        user.setName("User " + id);
        user.setEmail("user" + id + "@stockops.local");
        user.setRole(role);
        return user;
    }
}
