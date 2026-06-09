package com.stockops.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockops.ai.chat.dto.AiChatRequest;
import com.stockops.ai.provider.AiGenerationRequest;
import com.stockops.ai.provider.AiGenerationResponse;
import com.stockops.ai.provider.AiProviderFacade;
import com.stockops.ai.provider.AiServiceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AiChatControllerTest {

    @Mock AiProviderFacade providerFacade;
    MockMvc mockMvc;
    final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AiChatController(providerFacade)).build();
    }

    @Test
    void sendMessage_returnsAiResponseMappedToChatResponse() throws Exception {
        final AiGenerationResponse generation = new AiGenerationResponse(
                "재고 수준이 충분합니다.",
                "vertex",
                "gemini-2.5-flash",
                AiServiceStatus.FALLBACK_ACTIVE,
                true,
                "Bedrock unavailable",
                "Bedrock 연결 실패로 Vertex AI로 전환되었습니다.",
                null);
        when(providerFacade.generate(any())).thenReturn(generation);

        mockMvc.perform(post("/api/v1/ai/chat/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AiChatRequest("재고 현황 알려줘", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("재고 수준이 충분합니다."))
                .andExpect(jsonPath("$.provider").value("vertex"))
                .andExpect(jsonPath("$.fallbackUsed").value(true))
                .andExpect(jsonPath("$.fallbackNotice").value("Bedrock 연결 실패로 Vertex AI로 전환되었습니다."))
                .andExpect(jsonPath("$.serviceStatus").value("FALLBACK_ACTIVE"));
    }

    @Test
    void sendMessage_passesChatVisibleTrueToProvider() throws Exception {
        when(providerFacade.generate(any())).thenReturn(new AiGenerationResponse(
                "ok", "bedrock", "model", AiServiceStatus.AVAILABLE, false, null, null, null));
        final ArgumentCaptor<AiGenerationRequest> captor = ArgumentCaptor.forClass(AiGenerationRequest.class);

        mockMvc.perform(post("/api/v1/ai/chat/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AiChatRequest("hello", null, null))))
                .andExpect(status().isOk());

        verify(providerFacade).generate(captor.capture());
        assertThat(captor.getValue().chatVisible()).isTrue();
        assertThat(captor.getValue().useCase()).isEqualTo("CHAT");
    }
}
