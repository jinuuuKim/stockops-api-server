package com.stockops.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockops.ai.bedrock.BedrockAiFacade;
import com.stockops.ai.bedrock.dto.BedrockAgentInvokeRequest;
import com.stockops.ai.bedrock.dto.BedrockAgentInvokeResponse;
import com.stockops.ai.bedrock.dto.BedrockRagQueryRequest;
import com.stockops.ai.bedrock.dto.BedrockRagQueryResponse;
import com.stockops.service.ai.AIRecommendationService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BedrockAiControllerTest {

    @Mock AIRecommendationService recommendationService;
    @Mock BedrockAiFacade bedrockAiFacade;
    MockMvc mockMvc;
    final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new BedrockAiController(recommendationService, bedrockAiFacade)).build();
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
                new BedrockAgentInvokeResponse("재고 보충을 권장합니다.", "sess-1", true));

        mockMvc.perform(post("/api/v1/ai/bedrock/agent/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new BedrockAgentInvokeRequest("현황 분석", "sess-1", "WAREHOUSE", 2L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("재고 보충을 권장합니다."))
                .andExpect(jsonPath("$.actionSuggested").value(true));
    }
}
