package com.stockops.ai.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockops.ai.bedrock.agent.AgentToolCatalog;
import com.stockops.ai.bedrock.agent.AgentToolDispatcher;
import com.stockops.ai.bedrock.agent.AgentToolResult;
import com.stockops.ai.bedrock.chat.ChatHistoryProperties;
import com.stockops.ai.bedrock.chat.ChatHistoryStore;
import com.stockops.ai.bedrock.dto.BedrockAgentInvokeRequest;
import com.stockops.ai.bedrock.dto.BedrockAgentInvokeResponse;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

/**
 * Unit tests for {@link BedrockConverseOrchestrator}. The Bedrock client is mocked via the
 * factory; the tool-use loop, dispatch, guardrail wiring, and final-answer extraction run real.
 *
 * @author StockOps Team
 * @since 2.4
 */
@ExtendWith(MockitoExtension.class)
class BedrockConverseOrchestratorTest {

    @Mock
    private BedrockRuntimeClientFactory clientFactory;
    @Mock
    private AgentToolDispatcher toolDispatcher;
    @Mock
    private BedrockRuntimeClient client;

    private BedrockAiProperties properties;
    private BedrockConverseOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        properties = new BedrockAiProperties();
        properties.setEnabled(true);
        properties.setModelId("amazon.nova-micro-v1:0");
        @SuppressWarnings("unchecked")
        final ObjectProvider<ChatHistoryStore> historyProvider = mock(ObjectProvider.class);
        orchestrator = new BedrockConverseOrchestrator(
                clientFactory, properties, new AgentToolCatalog(), toolDispatcher, ObservationRegistry.NOOP,
                new ChatHistoryProperties(), historyProvider);
    }

    @Test
    void converseReturnsNotConfiguredWhenDisabled() {
        properties.setEnabled(false);

        final BedrockAgentInvokeResponse response =
                orchestrator.converse(new BedrockAgentInvokeRequest("질문", "s1", null, null), null);

        assertThat(response.answer()).contains("구성되지 않았");
        verify(clientFactory, never()).create(anyString());
    }

    @Test
    void converseReturnsFinalTextWithoutToolUse() {
        when(clientFactory.create(anyString())).thenReturn(client);
        when(client.converse(any(ConverseRequest.class)))
                .thenReturn(textResponse("창고 2 재고는 50개입니다."));

        final BedrockAgentInvokeResponse response =
                orchestrator.converse(new BedrockAgentInvokeRequest("창고 2 재고?", "s1", null, null), null);

        assertThat(response.answer()).isEqualTo("창고 2 재고는 50개입니다.");
        verify(toolDispatcher, never()).dispatch(anyString(), anyString());
    }

    @Test
    void converseDispatchesToolThenReturnsFinalAnswer() {
        when(clientFactory.create(anyString())).thenReturn(client);
        when(client.converse(any(ConverseRequest.class)))
                .thenReturn(toolUseResponse("call-1", "getInventoryRisk", Map.of("productId", 7)))
                .thenReturn(textResponse("상품 7 재고는 부족합니다."));
        when(toolDispatcher.dispatch(eq("getInventoryRisk"), anyString()))
                .thenReturn(AgentToolResult.success("getInventoryRisk", "[{\"quantity\":3}]"));

        final BedrockAgentInvokeResponse response =
                orchestrator.converse(new BedrockAgentInvokeRequest("상품 7 위험?", "s1", null, null), null);

        assertThat(response.answer()).isEqualTo("상품 7 재고는 부족합니다.");
        final ArgumentCaptor<String> inputCaptor = ArgumentCaptor.forClass(String.class);
        verify(toolDispatcher).dispatch(eq("getInventoryRisk"), inputCaptor.capture());
        assertThat(inputCaptor.getValue()).contains("\"productId\":7");
    }

    @Test
    void converseAttachesGuardrailConfigWhenConfigured() {
        properties.setGuardrailId("gr-123");
        properties.setGuardrailVersion("1");
        when(clientFactory.create(anyString())).thenReturn(client);
        when(client.converse(any(ConverseRequest.class))).thenReturn(textResponse("답변"));

        orchestrator.converse(new BedrockAgentInvokeRequest("질문", "s1", null, null), "운영 문서 발췌");

        final ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(client).converse(captor.capture());
        assertThat(captor.getValue().guardrailConfig()).isNotNull();
        assertThat(captor.getValue().guardrailConfig().guardrailIdentifier()).isEqualTo("gr-123");
        // document context is injected as an extra system block
        assertThat(captor.getValue().system()).hasSize(2);
        // tool catalog is always attached
        assertThat(captor.getValue().toolConfig()).isNotNull();
    }

    @Test
    void converseAppliesInferenceConfigFromProperties() {
        properties.setTemperature(0.2);
        properties.setMaxOutputTokens(1200);
        when(clientFactory.create(anyString())).thenReturn(client);
        when(client.converse(any(ConverseRequest.class))).thenReturn(textResponse("답변"));

        orchestrator.converse(new BedrockAgentInvokeRequest("질문", "s1", null, null), null);

        final ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(client).converse(captor.capture());
        assertThat(captor.getValue().inferenceConfig()).isNotNull();
        assertThat(captor.getValue().inferenceConfig().temperature()).isEqualTo(0.2f);
        assertThat(captor.getValue().inferenceConfig().maxTokens()).isEqualTo(1200);
    }

    @Test
    void converseUsesOverriddenSystemPromptWhenConfigured() {
        properties.setSystemPrompt("CUSTOM PROMPT");
        when(clientFactory.create(anyString())).thenReturn(client);
        when(client.converse(any(ConverseRequest.class))).thenReturn(textResponse("답변"));

        orchestrator.converse(new BedrockAgentInvokeRequest("질문", "s1", null, null), null);

        final ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(client).converse(captor.capture());
        assertThat(captor.getValue().system().get(0).text()).isEqualTo("CUSTOM PROMPT");
    }

    @Test
    void converseStopsAfterMaxToolTurns() {
        properties.setMaxToolTurns(2);
        when(clientFactory.create(anyString())).thenReturn(client);
        when(client.converse(any(ConverseRequest.class)))
                .thenReturn(toolUseResponse("c", "getInventoryRisk", Map.of()));
        when(toolDispatcher.dispatch(anyString(), anyString()))
                .thenReturn(AgentToolResult.success("getInventoryRisk", "[]"));

        final BedrockAgentInvokeResponse response =
                orchestrator.converse(new BedrockAgentInvokeRequest("질문", "s1", null, null), null);

        assertThat(response.answer()).contains("초과");
    }

    @Test
    void converseReturnsErrorAnswerWhenClientThrows() {
        when(clientFactory.create(anyString())).thenReturn(client);
        when(client.converse(any(ConverseRequest.class))).thenThrow(new RuntimeException("boom"));

        final BedrockAgentInvokeResponse response =
                orchestrator.converse(new BedrockAgentInvokeRequest("질문", "s1", null, null), null);

        assertThat(response.answer()).contains("오류");
    }

    private ConverseResponse textResponse(final String text) {
        return ConverseResponse.builder()
                .stopReason(StopReason.END_TURN)
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .role(ConversationRole.ASSISTANT)
                                .content(ContentBlock.fromText(text))
                                .build())
                        .build())
                .build();
    }

    private ConverseResponse toolUseResponse(final String toolUseId, final String name,
                                             final Map<String, Object> input) {
        final java.util.Map<String, Document> doc = new java.util.LinkedHashMap<>();
        input.forEach((key, value) -> doc.put(key, value instanceof Integer i
                ? Document.fromNumber(i) : Document.fromString(String.valueOf(value))));
        return ConverseResponse.builder()
                .stopReason(StopReason.TOOL_USE)
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .role(ConversationRole.ASSISTANT)
                                .content(ContentBlock.fromToolUse(ToolUseBlock.builder()
                                        .toolUseId(toolUseId)
                                        .name(name)
                                        .input(Document.fromMap(doc))
                                        .build()))
                                .build())
                        .build())
                .build();
    }
}
