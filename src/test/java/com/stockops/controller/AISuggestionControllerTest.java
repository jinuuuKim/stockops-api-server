package com.stockops.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockops.dto.ai.AISuggestionCreateRequest;
import com.stockops.dto.ai.AISuggestionExecuteRequest;
import com.stockops.dto.ai.AISuggestionRejectRequest;
import com.stockops.entity.User;
import com.stockops.entity.ai.AISuggestion;
import com.stockops.entity.ai.AISuggestionStatus;
import com.stockops.exception.ConflictException;
import com.stockops.security.CustomUserDetailsService;
import com.stockops.security.PermissionChecker;
import com.stockops.security.JwtTokenProvider;
import com.stockops.service.UserService;
import com.stockops.service.ai.AISuggestionService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class AISuggestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AISuggestionService aiSuggestionService;

    @MockBean
    private UserService userService;

    @MockBean
    private PermissionChecker permissionChecker;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private void stubPermissions(final boolean allowed) {
        when(permissionChecker.hasPermission(anyString())).thenReturn(allowed);
        when(permissionChecker.hasAnyPermission(any(String[].class))).thenReturn(allowed);
        when(permissionChecker.hasCenterScope(anyLong())).thenReturn(allowed);
        when(permissionChecker.hasWarehouseScope(anyLong())).thenReturn(allowed);
        when(permissionChecker.hasPermissionForCenter(anyString(), anyLong())).thenReturn(allowed);
        when(permissionChecker.hasPermissionForWarehouse(anyString(), anyLong())).thenReturn(allowed);
    }

    private User currentUser() {
        final User user = new User();
        user.setId(99L);
        user.setEmail("ai-admin@stockops.com");
        user.setName("AI Admin");
        return user;
    }

    private AISuggestion suggestion(final Long id, final AISuggestionStatus status) {
        final AISuggestion suggestion = new AISuggestion();
        suggestion.setId(id);
        suggestion.setType("PURCHASE_ORDER_CREATE");
        suggestion.setSeverity("WARNING");
        suggestion.setTitle("Restock Cola");
        suggestion.setSummary("Create purchase order suggestion");
        suggestion.setReason("Forecast indicates shortage");
        suggestion.setRecommendedAction("Create purchase order");
        suggestion.setTargetType("PURCHASE_ORDER");
        suggestion.setTargetId(123L);
        suggestion.setTargetScopeType("CENTER");
        suggestion.setTargetScopeId(10L);
        suggestion.setPayloadJson("{\"items\":[1]}");
        suggestion.setConfidenceScore(0.92);
        suggestion.setSource("AI_AGENT");
        suggestion.setSourceType("AI_AGENT");
        suggestion.setCreatedByUserId(99L);
        suggestion.setCreatedFromApp("admin-web");
        suggestion.setForecastSourceType("FORECAST");
        suggestion.setForecastSourceId(55L);
        suggestion.setForecastModelVersion("v1");
        suggestion.setForecastGeneratedAt(Instant.parse("2026-05-30T10:00:00Z"));
        suggestion.setForecastSourcePayloadJson("{\"forecast_total\":96}");
        suggestion.setStatus(status);
        suggestion.setVisibleToApp("ADMIN");
        suggestion.setApprovalMode("MANUAL_APPROVAL_REQUIRED");
        suggestion.setRequestedOnBehalfUserId(88L);
        suggestion.setRequestedScopeType("CENTER");
        suggestion.setRequestedScopeId(10L);
        suggestion.setExpiresAt(Instant.parse("2026-06-01T10:00:00Z"));
        suggestion.setReviewedByUserId(77L);
        suggestion.setReviewedAt(Instant.parse("2026-05-30T11:00:00Z"));
        suggestion.setApprovedByUserId(77L);
        suggestion.setApprovedAt(Instant.parse("2026-05-30T11:10:00Z"));
        suggestion.setExecutedAt(Instant.parse("2026-05-30T11:20:00Z"));
        suggestion.setRejectionReason("not needed");
        suggestion.setExecutionResult("{\"ok\":true}");
        suggestion.setCreatedAt(Instant.parse("2026-05-30T10:00:00Z"));
        suggestion.setUpdatedAt(Instant.parse("2026-05-30T11:20:00Z"));
        suggestion.setVersion(3L);
        return suggestion;
    }

    private AISuggestionCreateRequest createRequest() {
        return new AISuggestionCreateRequest(
                "PURCHASE_ORDER_CREATE",
                "WARNING",
                "Restock Cola",
                "Create purchase order suggestion",
                "Forecast indicates shortage",
                "Create purchase order",
                "PURCHASE_ORDER",
                123L,
                "CENTER",
                10L,
                "{\"items\":[1]}",
                0.92,
                "AI_AGENT",
                "AI_AGENT",
                "admin-web",
                "FORECAST",
                55L,
                "v1",
                Instant.parse("2026-05-30T10:00:00Z"),
                "{\"forecast_total\":96}",
                "ADMIN",
                "MANUAL_APPROVAL_REQUIRED",
                88L,
                "CENTER",
                10L,
                Instant.parse("2026-06-01T10:00:00Z"));
    }

    private void stubAuthentication() {
        when(jwtTokenProvider.validateToken("test-token")).thenReturn(true);
        when(jwtTokenProvider.extractEmail("test-token")).thenReturn("ai-admin");
        when(customUserDetailsService.loadUserByUsername("ai-admin")).thenReturn(
                org.springframework.security.core.userdetails.User.withUsername("ai-admin")
                        .password("n/a")
                        .authorities("ROLE_ADMIN")
                        .build());
    }

    @Test
    void listSuggestionsReturns200() throws Exception {
        stubPermissions(true);
        stubAuthentication();
        when(aiSuggestionService.list(any())).thenReturn(List.of(suggestion(1L, AISuggestionStatus.PENDING)));

        mockMvc.perform(get("/api/v1/ai/suggestions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].allowedActions[0]").value("APPROVE"))
                .andExpect(jsonPath("$[0].scopeMetadata.targetScopeType").value("CENTER"));
    }

    @Test
    void getSuggestionReturns200() throws Exception {
        stubPermissions(true);
        stubAuthentication();
        when(aiSuggestionService.detail(1L)).thenReturn(suggestion(1L, AISuggestionStatus.APPROVED));

        mockMvc.perform(get("/api/v1/ai/suggestions/{id}", 1L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.allowedActions[0]").value("EXECUTE"));
    }

    @Test
    void createSuggestionReturns201() throws Exception {
        stubPermissions(true);
        stubAuthentication();
        when(userService.getUserByEmail("ai-admin")).thenReturn(currentUser());
        when(aiSuggestionService.create(any(), any(), any())).thenReturn(suggestion(1L, AISuggestionStatus.PENDING));

        mockMvc.perform(post("/api/v1/ai/suggestions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                        .header("X-Request-Id", "req-create-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.allowedActions[0]").value("APPROVE"));

        verify(aiSuggestionService).create(any(), any(), any());
    }

    @Test
    void createSuggestionWithInvalidPayloadReturns400() throws Exception {
        stubPermissions(true);
        stubAuthentication();

        mockMvc.perform(post("/api/v1/ai/suggestions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approveSuggestionReturns200() throws Exception {
        stubPermissions(true);
        stubAuthentication();
        when(userService.getUserByEmail("ai-admin")).thenReturn(currentUser());
        when(aiSuggestionService.approve(anyLong(), any(), anyString()))
                .thenReturn(suggestion(1L, AISuggestionStatus.APPROVED));

        mockMvc.perform(post("/api/v1/ai/suggestions/{id}/approve", 1L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                        .header("X-Request-Id", "req-approve-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.allowedActions[0]").value("EXECUTE"));
    }

    @Test
    void rejectSuggestionReturns200() throws Exception {
        stubPermissions(true);
        stubAuthentication();
        when(userService.getUserByEmail("ai-admin")).thenReturn(currentUser());
        when(aiSuggestionService.reject(anyLong(), anyString(), any(), anyString()))
                .thenReturn(suggestion(1L, AISuggestionStatus.REJECTED));

        mockMvc.perform(post("/api/v1/ai/suggestions/{id}/reject", 1L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                        .header("X-Request-Id", "req-reject-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AISuggestionRejectRequest("not needed"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.errorMessage").value("not needed"));
    }

    @Test
    void executeSuggestionReturns200() throws Exception {
        stubPermissions(true);
        stubAuthentication();
        when(userService.getUserByEmail("ai-admin")).thenReturn(currentUser());
        when(aiSuggestionService.execute(anyLong(), any(), any(), anyString()))
                .thenReturn(suggestion(1L, AISuggestionStatus.EXECUTED));

        mockMvc.perform(post("/api/v1/ai/suggestions/{id}/execute", 1L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                        .header("X-Request-Id", "req-execute-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AISuggestionExecuteRequest("{\"ok\":true}"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.allowedActions").isEmpty());
    }

    @Test
    void executeSuggestionConflictReturns409() throws Exception {
        stubPermissions(true);
        stubAuthentication();
        when(userService.getUserByEmail("ai-admin")).thenReturn(currentUser());
        when(aiSuggestionService.execute(anyLong(), any(), any(), anyString()))
                .thenThrow(new ConflictException("AI suggestion was modified by another request"));

        mockMvc.perform(post("/api/v1/ai/suggestions/{id}/execute", 1L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                        .header("X-Request-Id", "req-execute-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AISuggestionExecuteRequest("{\"ok\":true}"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void requestWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/ai/suggestions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithoutPermissionReturns403() throws Exception {
        stubPermissions(false);
        stubAuthentication();

        mockMvc.perform(get("/api/v1/ai/suggestions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andExpect(status().isForbidden());
    }
}
