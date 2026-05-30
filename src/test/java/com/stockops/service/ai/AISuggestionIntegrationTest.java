package com.stockops.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockops.dto.ai.AISuggestionCreateRequest;
import com.stockops.dto.ai.AISuggestionExecuteRequest;
import com.stockops.dto.ai.AISuggestionRejectRequest;
import com.stockops.entity.Role;
import com.stockops.entity.User;
import com.stockops.entity.ai.AISuggestion;
import com.stockops.entity.ai.AISuggestionAudit;
import com.stockops.entity.ai.AISuggestionStatus;
import com.stockops.exception.ForbiddenException;
import com.stockops.exception.InvalidOperationException;
import com.stockops.repository.InventoryRepository;
import com.stockops.repository.PurchaseOrderRepository;
import com.stockops.repository.ai.AISuggestionAuditRepository;
import com.stockops.repository.ai.AISuggestionRepository;
import com.stockops.security.AISuggestionPermissions;
import com.stockops.security.CustomUserDetailsService;
import com.stockops.security.JwtTokenProvider;
import com.stockops.security.PermissionChecker;
import com.stockops.security.ScopeGuard;
import com.stockops.service.UserService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class AISuggestionIntegrationTest {

    private static final String TOKEN = "ai-suggestion-token";
    private static final String AUTH_EMAIL = "ai-admin@stockops.local";
    private static final Long WAREHOUSE_ID = 456L;
    private static final Long CENTER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AISuggestionService aiSuggestionService;

    @Autowired
    private AISuggestionRepository aiSuggestionRepository;

    @Autowired
    private AISuggestionAuditRepository aiSuggestionAuditRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @MockBean
    private PermissionChecker permissionChecker;

    @MockBean
    private ScopeGuard scopeGuard;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void toolSeamCreatePersistsPendingSuggestionAndAuditWithoutBusinessWrites() throws Exception {
        stubAllPermissions(true);
        stubAuthentication();
        when(userService.getUserByEmail(AUTH_EMAIL)).thenReturn(user(501L, "AI Admin", "MANAGER"));
        final long purchaseOrderCountBefore = purchaseOrderRepository.count();
        final long inventoryCountBefore = inventoryRepository.count();
        final long inventoryQuantityBefore = inventoryRepository.sumInventoryQuantity();

        final MockHttpServletResponse response = exchange(
                HttpMethod.POST,
                "/api/v1/ai/suggestions",
                createRequest(),
                true,
                "req-tool-create");

        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED.value());
        final JsonNode payload = objectMapper.readTree(response.getContentAsString());
        final long suggestionId = payload.path("id").asLong();
        assertThat(payload.path("status").asText()).isEqualTo("PENDING");
        assertThat(payload.path("allowedActions").get(0).asText()).isEqualTo("APPROVE");
        assertThat(payload.path("allowedActions").get(1).asText()).isEqualTo("REJECT");
        assertThat(purchaseOrderRepository.count()).isEqualTo(purchaseOrderCountBefore);
        assertThat(inventoryRepository.count()).isEqualTo(inventoryCountBefore);
        assertThat(inventoryRepository.sumInventoryQuantity()).isEqualTo(inventoryQuantityBefore);

        final AISuggestion saved = aiSuggestionRepository.findById(suggestionId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(AISuggestionStatus.PENDING);
        assertThat(saved.getForecastSourceType()).isEqualTo("AI_FORECAST");
        assertThat(saved.getForecastModelVersion()).isEqualTo("forecast-v5");
        assertThat(saved.getCreatedByUserId()).isEqualTo(501L);

        final List<AISuggestionAudit> audits = aiSuggestionAuditRepository.findBySuggestionIdOrderByRecordedAtAsc(suggestionId);
        assertThat(audits).hasSize(1);
        assertThat(audits.getFirst().getAction()).isEqualTo("CREATE");
        assertThat(audits.getFirst().getAfterStatus()).isEqualTo("PENDING");
        assertThat(audits.getFirst().getRequestId()).isEqualTo("req-tool-create");
    }

    @Test
    void lifecycleHappyPathsCreateAuditsForCreateApproveRejectAndExecute() {
        stubAllPermissions(true);
        final User manager = user(502L, "Lifecycle Manager", "MANAGER");

        final AISuggestion pendingForExecution = aiSuggestionService.create(createCommand(), manager, "req-create-execute");
        final AISuggestion approved = aiSuggestionService.approve(pendingForExecution.getId(), manager, "req-approve");
        final AISuggestion executed = aiSuggestionService.execute(approved.getId(), "{\"purchaseOrderId\":321}", manager, "req-execute");

        assertThat(executed.getStatus()).isEqualTo(AISuggestionStatus.EXECUTED);
        assertThat(executed.getExecutionResult()).isEqualTo("{\"purchaseOrderId\":321}");
        assertThat(aiSuggestionAuditRepository.findBySuggestionIdOrderByRecordedAtAsc(executed.getId()))
                .extracting(AISuggestionAudit::getAction)
                .containsExactly("CREATE", "APPROVE", "EXECUTE");

        final AISuggestion pendingForRejection = aiSuggestionService.create(createCommand(), manager, "req-create-reject");
        final AISuggestion rejected = aiSuggestionService.reject(pendingForRejection.getId(), "supplier lead time changed", manager, "req-reject");

        assertThat(rejected.getStatus()).isEqualTo(AISuggestionStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo("supplier lead time changed");
        assertThat(aiSuggestionAuditRepository.findBySuggestionIdOrderByRecordedAtAsc(rejected.getId()))
                .extracting(AISuggestionAudit::getAction)
                .containsExactly("CREATE", "REJECT");
    }

    @Test
    void invalidTransitionsAndExpiredForecastMetadataFailBeforeMutationOrAudit() {
        stubAllPermissions(true);
        final User manager = user(503L, "Transition Manager", "MANAGER");

        final AISuggestion approved = aiSuggestionRepository.save(suggestion(AISuggestionStatus.APPROVED));
        assertThatThrownBy(() -> aiSuggestionService.approve(approved.getId(), manager, "req-approve-again"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APPROVED to APPROVED");
        assertThat(aiSuggestionRepository.findById(approved.getId()).orElseThrow().getStatus())
                .isEqualTo(AISuggestionStatus.APPROVED);
        assertThat(aiSuggestionAuditRepository.findBySuggestionIdOrderByRecordedAtAsc(approved.getId())).isEmpty();

        final AISuggestion pending = aiSuggestionRepository.save(suggestion(AISuggestionStatus.PENDING));
        assertThatThrownBy(() -> aiSuggestionService.execute(pending.getId(), "{}", manager, "req-execute-pending"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING to EXECUTED");
        assertThat(aiSuggestionRepository.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(AISuggestionStatus.PENDING);
        assertThat(aiSuggestionAuditRepository.findBySuggestionIdOrderByRecordedAtAsc(pending.getId())).isEmpty();

        final AISuggestion expiredPending = expiredSuggestion(AISuggestionStatus.PENDING);
        final AISuggestion savedExpiredPending = aiSuggestionRepository.save(expiredPending);
        assertThatThrownBy(() -> aiSuggestionService.approve(savedExpiredPending.getId(), manager, "req-approve-expired"))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Expired");
        assertThat(aiSuggestionRepository.findById(savedExpiredPending.getId()).orElseThrow().getStatus())
                .isEqualTo(AISuggestionStatus.PENDING);
        assertThat(aiSuggestionAuditRepository.findBySuggestionIdOrderByRecordedAtAsc(savedExpiredPending.getId())).isEmpty();

        final AISuggestion expiredApproved = aiSuggestionRepository.save(expiredSuggestion(AISuggestionStatus.APPROVED));
        assertThatThrownBy(() -> aiSuggestionService.execute(expiredApproved.getId(), "{}", manager, "req-execute-expired"))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Expired");
        assertThat(aiSuggestionRepository.findById(expiredApproved.getId()).orElseThrow().getStatus())
                .isEqualTo(AISuggestionStatus.APPROVED);
        assertThat(aiSuggestionAuditRepository.findBySuggestionIdOrderByRecordedAtAsc(expiredApproved.getId())).isEmpty();
    }

    @Test
    void controllerStatusMatrixCovers200400401And403() throws Exception {
        stubAllPermissions(true);
        stubAuthentication();
        when(userService.getUserByEmail(AUTH_EMAIL)).thenReturn(user(504L, "Controller Manager", "MANAGER"));

        final MockHttpServletResponse createResponse = exchange(
                HttpMethod.POST,
                "/api/v1/ai/suggestions",
                createRequest(),
                true,
                "req-status-create");
        assertThat(createResponse.getStatus()).isEqualTo(HttpStatus.CREATED.value());
        final long suggestionId = objectMapper.readTree(createResponse.getContentAsString()).path("id").asLong();

        final MockHttpServletResponse approveResponse = exchange(
                HttpMethod.POST,
                "/api/v1/ai/suggestions/" + suggestionId + "/approve",
                null,
                true,
                "req-status-approve");
        assertThat(approveResponse.getStatus()).isEqualTo(HttpStatus.OK.value());

        final MockHttpServletResponse blankRejectResponse = exchange(
                HttpMethod.POST,
                "/api/v1/ai/suggestions/" + suggestionId + "/reject",
                new AISuggestionRejectRequest(" "),
                true,
                "req-status-reject-blank");
        assertThat(blankRejectResponse.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());

        final MockHttpServletResponse unauthenticatedResponse = exchange(
                HttpMethod.GET,
                "/api/v1/ai/suggestions",
                null,
                false,
                null);
        assertThat(unauthenticatedResponse.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());

        stubAllPermissions(false);
        final MockHttpServletResponse forbiddenResponse = exchange(
                HttpMethod.POST,
                "/api/v1/ai/suggestions",
                createRequest(),
                true,
                "req-status-forbidden");
        assertThat(forbiddenResponse.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void rbacDenialsCoverEveryControllerAction() throws Exception {
        stubAllPermissions(false);
        stubAuthentication();

        assertThat(exchange(HttpMethod.GET, "/api/v1/ai/suggestions", null, true, null).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(exchange(HttpMethod.GET, "/api/v1/ai/suggestions/1", null, true, null).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(exchange(HttpMethod.POST, "/api/v1/ai/suggestions", createRequest(), true, "req-create-denied").getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(exchange(HttpMethod.POST, "/api/v1/ai/suggestions/1/approve", null, true, "req-approve-denied").getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(exchange(HttpMethod.POST, "/api/v1/ai/suggestions/1/reject", new AISuggestionRejectRequest("no"), true, "req-reject-denied").getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(exchange(HttpMethod.POST, "/api/v1/ai/suggestions/1/execute", new AISuggestionExecuteRequest("{}"), true, "req-execute-denied").getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void outOfScopeDenialsCoverEveryServiceAction() {
        stubAllPermissions(true);
        doThrow(new ForbiddenException("Access denied for warehouse: " + WAREHOUSE_ID))
                .when(scopeGuard).assertWarehouseAccess(WAREHOUSE_ID);
        final User manager = user(505L, "Scope Manager", "MANAGER");
        final AISuggestion pending = aiSuggestionRepository.save(suggestion(AISuggestionStatus.PENDING));
        final AISuggestion approved = aiSuggestionRepository.save(suggestion(AISuggestionStatus.APPROVED));

        assertThatThrownBy(() -> aiSuggestionService.create(createCommand(), manager, "req-create-scope"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("warehouse");
        assertThatThrownBy(() -> aiSuggestionService.list(new AISuggestionService.ListQuery(null, "WAREHOUSE", WAREHOUSE_ID)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("warehouse");
        assertThatThrownBy(() -> aiSuggestionService.detail(pending.getId()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("warehouse");
        assertThatThrownBy(() -> aiSuggestionService.approve(pending.getId(), manager, "req-approve-scope"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("warehouse");
        assertThatThrownBy(() -> aiSuggestionService.reject(pending.getId(), "no", manager, "req-reject-scope"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("warehouse");
        assertThatThrownBy(() -> aiSuggestionService.execute(approved.getId(), "{}", manager, "req-execute-scope"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("warehouse");
        assertThat(aiSuggestionAuditRepository.findAll()).isEmpty();
    }

    private void stubAllPermissions(final boolean allowed) {
        when(permissionChecker.hasPermission(anyString())).thenReturn(allowed);
        when(permissionChecker.hasAnyPermission(any(String[].class))).thenReturn(allowed);
        when(permissionChecker.hasCenterScope(anyLong())).thenReturn(allowed);
        when(permissionChecker.hasWarehouseScope(anyLong())).thenReturn(allowed);
        when(permissionChecker.hasPermissionForCenter(anyString(), anyLong())).thenReturn(allowed);
        when(permissionChecker.hasPermissionForWarehouse(anyString(), anyLong())).thenReturn(allowed);
        when(permissionChecker.hasPermission(AISuggestionPermissions.READ)).thenReturn(allowed);
        when(permissionChecker.hasPermission(AISuggestionPermissions.CREATE)).thenReturn(allowed);
        when(permissionChecker.hasPermission(AISuggestionPermissions.APPROVE)).thenReturn(allowed);
        when(permissionChecker.hasPermission(AISuggestionPermissions.REJECT)).thenReturn(allowed);
        when(permissionChecker.hasPermission(AISuggestionPermissions.EXECUTE)).thenReturn(allowed);
    }

    private void stubAuthentication() {
        when(jwtTokenProvider.validateToken(TOKEN)).thenReturn(true);
        when(jwtTokenProvider.extractEmail(TOKEN)).thenReturn(AUTH_EMAIL);
        when(customUserDetailsService.loadUserByUsername(AUTH_EMAIL)).thenReturn(
                org.springframework.security.core.userdetails.User.withUsername(AUTH_EMAIL)
                        .password("n/a")
                        .authorities("ROLE_ADMIN")
                        .build());
    }

    private MockHttpServletResponse exchange(final HttpMethod method,
                                             final String path,
                                             final Object body,
                                             final boolean authenticated,
                                             final String requestId) throws Exception {
        final MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders
                .request(method, path)
                .contentType(MediaType.APPLICATION_JSON);
        if (authenticated) {
            requestBuilder.header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
        }
        if (requestId != null) {
            requestBuilder.header("X-Request-Id", requestId);
        }
        if (body != null) {
            requestBuilder.content(objectMapper.writeValueAsBytes(body));
        }

        final MvcResult result = mockMvc.perform(requestBuilder).andReturn();
        return result.getResponse();
    }

    private AISuggestionCreateRequest createRequest() {
        return new AISuggestionCreateRequest(
                "PURCHASE_ORDER_CREATE",
                "HIGH",
                "Restock milk",
                "Milk forecast is below threshold",
                "Expected demand exceeds current stock",
                "Create draft purchase order",
                "PURCHASE_ORDER",
                123L,
                "WAREHOUSE",
                WAREHOUSE_ID,
                "{\"quantity\":100,\"supplierId\":42}",
                0.92D,
                "bedrock-agent",
                "AI_AGENT",
                "admin-web",
                "AI_FORECAST",
                77L,
                "forecast-v5",
                Instant.parse("2026-05-30T09:00:00Z"),
                "{\"forecast_total\":120}",
                "BOTH",
                "MANUAL_APPROVAL_REQUIRED",
                88L,
                "WAREHOUSE",
                WAREHOUSE_ID,
                Instant.now().plusSeconds(3600));
    }

    private AISuggestionService.CreateCommand createCommand() {
        return createRequest().toCommand();
    }

    private AISuggestion suggestion(final AISuggestionStatus status) {
        final AISuggestion suggestion = new AISuggestion();
        suggestion.setType("PURCHASE_ORDER_CREATE");
        suggestion.setSeverity("HIGH");
        suggestion.setTitle("Restock milk");
        suggestion.setSummary("Milk forecast is below threshold");
        suggestion.setReason("Expected demand exceeds current stock");
        suggestion.setRecommendedAction("Create draft purchase order");
        suggestion.setTargetType("PURCHASE_ORDER");
        suggestion.setTargetId(123L);
        suggestion.setTargetScopeType("WAREHOUSE");
        suggestion.setTargetScopeId(WAREHOUSE_ID);
        suggestion.setPayloadJson("{\"quantity\":100}");
        suggestion.setConfidenceScore(0.92D);
        suggestion.setSource("bedrock-agent");
        suggestion.setSourceType("AI_AGENT");
        suggestion.setCreatedFromApp("admin-web");
        suggestion.setForecastSourceType("AI_FORECAST");
        suggestion.setForecastSourceId(77L);
        suggestion.setForecastModelVersion("forecast-v5");
        suggestion.setForecastGeneratedAt(Instant.parse("2026-05-30T09:00:00Z"));
        suggestion.setForecastSourcePayloadJson("{\"forecast_total\":120}");
        suggestion.setStatus(status);
        suggestion.setVisibleToApp("BOTH");
        suggestion.setApprovalMode("MANUAL_APPROVAL_REQUIRED");
        suggestion.setRequestedOnBehalfUserId(88L);
        suggestion.setRequestedScopeType("WAREHOUSE");
        suggestion.setRequestedScopeId(WAREHOUSE_ID);
        suggestion.setExpiresAt(Instant.now().plusSeconds(3600));
        return suggestion;
    }

    private AISuggestion expiredSuggestion(final AISuggestionStatus status) {
        final AISuggestion suggestion = suggestion(status);
        suggestion.setForecastGeneratedAt(Instant.now().minusSeconds(86_400));
        suggestion.setExpiresAt(Instant.now().minusSeconds(60));
        return suggestion;
    }

    private User user(final Long id, final String name, final String roleName) {
        final Role role = new Role();
        role.setName(roleName);

        final User user = new User();
        user.setId(id);
        user.setName(name);
        user.setEmail(name.toLowerCase().replace(' ', '.') + "@stockops.local");
        user.setRole(role);
        return user;
    }
}
