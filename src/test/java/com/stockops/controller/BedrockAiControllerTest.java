package com.stockops.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockops.ai.bedrock.AiRagRateLimiter;
import com.stockops.ai.bedrock.BedrockAiFacade;
import com.stockops.ai.bedrock.BedrockConverseOrchestrator;
import com.stockops.ai.bedrock.KnowledgeBaseContextProvider;
import com.stockops.ai.bedrock.dto.BedrockAgentInvokeRequest;
import com.stockops.ai.bedrock.dto.BedrockAgentInvokeResponse;
import com.stockops.ai.bedrock.dto.BedrockOpsSummaryResponse;
import com.stockops.ai.bedrock.job.AssistantJob;
import com.stockops.ai.bedrock.job.AssistantJobService;
import com.stockops.ai.bedrock.dto.BedrockRagQueryRequest;
import com.stockops.ai.bedrock.dto.BedrockRagQueryResponse;
import com.stockops.ai.bedrock.dto.BedrockRecommendationExplanationResponse;
import com.stockops.dto.AIRecommendationDTO;
import com.stockops.entity.ai.AIRecommendationStatus;
import com.stockops.service.ai.AIRecommendationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BedrockAiControllerTest {

    @Mock AIRecommendationService recommendationService;
    @Mock BedrockAiFacade bedrockAiFacade;
    @Mock AiRagRateLimiter ragRateLimiter;
    @Mock BedrockConverseOrchestrator converseOrchestrator;
    @Mock KnowledgeBaseContextProvider knowledgeBaseContextProvider;
    @Mock AssistantJobService assistantJobService;
    MockMvc mockMvc;
    final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new BedrockAiController(recommendationService, bedrockAiFacade, ragRateLimiter,
                        converseOrchestrator, knowledgeBaseContextProvider, assistantJobService)).build();
    }

    @Test
    void queryKnowledgeBase_returns200WithAnswer() throws Exception {
        when(bedrockAiFacade.queryKnowledgeBase(any())).thenReturn(
                new BedrockRagQueryResponse("창고 2의 재고 기준은 50입니다.", List.of("s3://kb/doc.pdf"), null));

        mockMvc.perform(post("/api/v1/ai/bedrock/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new BedrockRagQueryRequest("창고 2 재고 기준을 알려줘", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("창고 2의 재고 기준은 50입니다."))
                .andExpect(jsonPath("$.citations[0]").value("s3://kb/doc.pdf"));
    }

    @Test
    void invokeAgent_returns200WithAnswer() throws Exception {
        when(bedrockAiFacade.invokeAgent(any())).thenReturn(
                BedrockAgentInvokeResponse.of("재고 보충을 권장합니다.", "sess-1", true));

        mockMvc.perform(post("/api/v1/ai/bedrock/agent/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new BedrockAgentInvokeRequest("현황 분석", "sess-1", "WAREHOUSE", 2L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("재고 보충을 권장합니다."))
                .andExpect(jsonPath("$.actionSuggested").value(true));
    }

    @Test
    void assistant_returns200AndInjectsKnowledgeBaseContext() throws Exception {
        when(knowledgeBaseContextProvider.retrieveContext(any())).thenReturn("운영 매뉴얼 발췌");
        when(converseOrchestrator.converse(any(), any())).thenReturn(
                BedrockAgentInvokeResponse.of("창고 2 재고는 충분합니다.", "sess-9", false));

        mockMvc.perform(post("/api/v1/ai/bedrock/assistant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new BedrockAgentInvokeRequest("창고 2 재고 알려줘", "sess-9", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("창고 2 재고는 충분합니다."));

        verify(knowledgeBaseContextProvider).retrieveContext("창고 2 재고 알려줘");
        verify(converseOrchestrator).converse(any(), any());
    }

    @Test
    void createAssistantJob_returns202WithJobId() throws Exception {
        when(assistantJobService.createJob(any())).thenReturn("job-123");

        mockMvc.perform(post("/api/v1/ai/bedrock/assistant/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new BedrockAgentInvokeRequest("창고 2 재고 알려줘", null, null, null))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value("job-123"));
    }

    @Test
    void getAssistantJob_returnsDoneResultWhenComplete() throws Exception {
        when(assistantJobService.getJob("job-123")).thenReturn(
                AssistantJob.done(BedrockAgentInvokeResponse.of("창고 2 재고는 충분합니다.", "sess-9", false)));

        mockMvc.perform(get("/api/v1/ai/bedrock/assistant/jobs/job-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.result.answer").value("창고 2 재고는 충분합니다."));
    }

    @Test
    void getAssistantJob_returns404WhenUnknown() throws Exception {
        when(assistantJobService.getJob("missing")).thenReturn(null);

        mockMvc.perform(get("/api/v1/ai/bedrock/assistant/jobs/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void explainRecommendation_returns200WithExplanationFields() throws Exception {
        final AIRecommendationDTO dto = sampleDto();
        when(recommendationService.detailRecommendation(anyLong())).thenReturn(dto);
        when(bedrockAiFacade.explainRecommendation(any())).thenReturn(
                new BedrockRecommendationExplanationResponse(
                        1L, "재고 부족 위험", List.of("안전재고 이하"),
                        List.of("납기 확인"), "HIGH", "anthropic.claude-3-haiku", Instant.now()));

        mockMvc.perform(post("/api/v1/ai/bedrock/recommendations/1/explain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendationId").value(1))
                .andExpect(jsonPath("$.summary").value("재고 부족 위험"))
                .andExpect(jsonPath("$.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.reasons[0]").value("안전재고 이하"))
                .andExpect(jsonPath("$.modelId").value("anthropic.claude-3-haiku"));
    }

    @Test
    void opsSummary_returns200WithSourceCountsAndConfidenceCaveat() throws Exception {
        when(bedrockAiFacade.summarizeOperations(any(), any(), any())).thenReturn(
                new BedrockOpsSummaryResponse(
                        LocalDate.of(2026, 6, 10), 1L, 1L,
                        "운영 위험 높음",
                        List.of("재고 부족"),
                        List.of("즉시 발주"),
                        "HIGH",
                        Instant.now(),
                        Map.of("recommendations", 3, "sensorAlerts", 2,
                                "criticalExpiry", 1, "warningExpiry", 0,
                                "overdueShipments", 1, "inventoryBelowSafetyStock", 4,
                                "recentPrivilegeEvents", 1),
                        "추천 3건, 센서 알림 2건을 기반으로 생성되었습니다."));

        mockMvc.perform(get("/api/v1/ai/bedrock/ops-summary")
                        .param("businessDate", "2026-06-10")
                        .param("centerId", "1")
                        .param("warehouseId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("운영 위험 높음"))
                .andExpect(jsonPath("$.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.sourceCounts.recommendations").value(3))
                .andExpect(jsonPath("$.sourceCounts.overdueShipments").value(1))
                .andExpect(jsonPath("$.confidenceCaveat").value("추천 3건, 센서 알림 2건을 기반으로 생성되었습니다."));
    }

    @Test
    void queryKnowledgeBase_invokesRateLimiterWhenAuthenticationIsNull() throws Exception {
        // Standalone MockMvc has no Authentication; rate limiter should be skipped (null-guard)
        when(bedrockAiFacade.queryKnowledgeBase(any())).thenReturn(
                new BedrockRagQueryResponse("답변입니다.", List.of(), null));

        mockMvc.perform(post("/api/v1/ai/bedrock/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new BedrockRagQueryRequest("질문", null, null))))
                .andExpect(status().isOk());

        // Authentication is null in standalone MockMvc, so checkRagLimit must NOT be called
        verify(ragRateLimiter, org.mockito.Mockito.never()).checkRagLimit(any());
    }

    private AIRecommendationDTO sampleDto() {
        return new AIRecommendationDTO(
                1L, LocalDate.of(2026, 6, 10),
                100L, "샘플 상품", "BAR-001",
                1L, 1L,
                AIRecommendationStatus.READY_FOR_APPROVAL,
                10, 5, 50, 48, 3, 15,
                BigDecimal.valueOf(7.5), BigDecimal.valueOf(8.0), BigDecimal.valueOf(7.8),
                30, false, null, null, null, null, null,
                "v2.0.0",
                Instant.parse("2026-06-10T00:00:00Z"),
                Instant.parse("2026-06-10T00:00:00Z"));
    }
}
