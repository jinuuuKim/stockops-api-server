package com.stockops.ai.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockops.ai.bedrock.agent.AgentToolDispatcher;
import com.stockops.ai.bedrock.agent.AgentToolResult;
import com.stockops.ai.bedrock.dto.BedrockAgentInvokeRequest;
import com.stockops.ai.bedrock.dto.BedrockAgentInvokeResponse;
import com.stockops.ai.bedrock.dto.BedrockRagQueryRequest;
import com.stockops.ai.bedrock.dto.BedrockRagQueryResponse;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.FunctionInvocationInput;
import software.amazon.awssdk.services.bedrockagentruntime.model.FunctionParameter;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvocationInputMember;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeAgentRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateOutput;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateResponse;
import software.amazon.awssdk.services.bedrockagentruntime.model.ReturnControlPayload;
import software.amazon.awssdk.services.bedrockagentruntime.model.SessionState;

/**
 * Unit tests for the Bedrock Agent return-control loop in
 * {@link BedrockAgentRuntimeClientAdapter}. The streamed turn transport is
 * stubbed via {@code executeTurn}; the loop, tool dispatch, and session-state
 * continuation logic run for real.
 *
 * @author StockOps Team
 * @since 2.0
 */
@ExtendWith(MockitoExtension.class)
class BedrockAgentRuntimeClientAdapterTest {

    @Mock
    private AgentToolDispatcher toolDispatcher;

    @Mock
    private BedrockAgentRuntimeClientFactory clientFactory;

    @Mock
    private BedrockAgentRuntimeAsyncClient asyncClient;

    @Mock
    private BedrockAgentRuntimeClient syncClient;

    private BedrockAiProperties properties;
    private BedrockAgentRuntimeClientAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new BedrockAiProperties();
        properties.setEnabled(true);
        properties.setAgentId("AGENT123");
        properties.setAgentAliasId("ALIAS123");
        adapter = spy(new BedrockAgentRuntimeClientAdapter(
                properties, toolDispatcher, clientFactory, ObservationRegistry.NOOP));
    }

    /**
     * Verifies the adapter declines cleanly when the agent is not configured.
     */
    @Test
    void invokeAgentReturnsNotConfiguredWhenAgentIdsMissing() {
        properties.setAgentId("");

        final BedrockAgentInvokeResponse response =
                adapter.invokeAgent(new BedrockAgentInvokeRequest("재고 상태 알려줘", "session-1", null, null));

        assertThat(response.answer()).contains("not configured");
        verify(clientFactory, never()).createAsyncClient(anyString());
    }

    /**
     * Verifies a single-turn final answer is returned as-is with the caller's session id.
     */
    @Test
    void invokeAgentReturnsFinalAnswerWithoutToolCalls() throws Exception {
        when(clientFactory.createAsyncClient(anyString())).thenReturn(asyncClient);
        doReturn(new BedrockAgentRuntimeClientAdapter.AgentTurn("최종 답변입니다.", null))
                .when(adapter).executeTurn(eq(asyncClient), any(InvokeAgentRequest.class));

        final BedrockAgentInvokeResponse response =
                adapter.invokeAgent(new BedrockAgentInvokeRequest("재고 상태 알려줘", "session-1", null, null));

        assertThat(response.answer()).isEqualTo("최종 답변입니다.");
        assertThat(response.sessionId()).isEqualTo("session-1");
        verify(toolDispatcher, never()).dispatch(anyString(), anyString());
    }

    /**
     * Verifies a blank session id is replaced with a generated one.
     */
    @Test
    void invokeAgentGeneratesSessionIdWhenAbsent() throws Exception {
        when(clientFactory.createAsyncClient(anyString())).thenReturn(asyncClient);
        doReturn(new BedrockAgentRuntimeClientAdapter.AgentTurn("answer", null))
                .when(adapter).executeTurn(eq(asyncClient), any(InvokeAgentRequest.class));

        final BedrockAgentInvokeResponse response =
                adapter.invokeAgent(new BedrockAgentInvokeRequest("질문", null, null, null));

        assertThat(response.sessionId()).isNotBlank();
    }

    /**
     * Verifies the full return-control round trip: the requested tool is dispatched
     * with JSON-shaped parameters and its result is sent back through session state
     * before the final answer is returned.
     */
    @Test
    void invokeAgentDispatchesReturnControlToolAndContinues() throws Exception {
        when(clientFactory.createAsyncClient(anyString())).thenReturn(asyncClient);
        final ReturnControlPayload payload = returnControlPayload("inv-1", "getProphetForecast",
                FunctionParameter.builder().name("productId").value("1").build(),
                FunctionParameter.builder().name("days").value("7").build());
        doReturn(new BedrockAgentRuntimeClientAdapter.AgentTurn("", payload),
                new BedrockAgentRuntimeClientAdapter.AgentTurn("예측 결과 설명입니다.", null))
                .when(adapter).executeTurn(eq(asyncClient), any(InvokeAgentRequest.class));
        when(toolDispatcher.dispatch(eq("getProphetForecast"), anyString()))
                .thenReturn(AgentToolResult.success("getProphetForecast", "{\"forecast\":[]}"));

        final BedrockAgentInvokeResponse response =
                adapter.invokeAgent(new BedrockAgentInvokeRequest("1번 상품 수요 예측해줘", "session-1", null, null));

        assertThat(response.answer()).isEqualTo("예측 결과 설명입니다.");

        final ArgumentCaptor<String> inputCaptor = ArgumentCaptor.forClass(String.class);
        verify(toolDispatcher).dispatch(eq("getProphetForecast"), inputCaptor.capture());
        assertThat(inputCaptor.getValue()).contains("\"productId\":\"1\"").contains("\"days\":\"7\"");

        final ArgumentCaptor<InvokeAgentRequest> requestCaptor = ArgumentCaptor.forClass(InvokeAgentRequest.class);
        verify(adapter, times(2)).executeTurn(eq(asyncClient), requestCaptor.capture());
        final InvokeAgentRequest continuation = requestCaptor.getAllValues().get(1);
        assertThat(continuation.sessionState().invocationId()).isEqualTo("inv-1");
        assertThat(continuation.sessionState().returnControlInvocationResults()).hasSize(1);
        assertThat(continuation.sessionState().returnControlInvocationResults().get(0)
                .functionResult().responseBody().get("TEXT").body()).isEqualTo("{\"forecast\":[]}");
    }

    /**
     * Verifies failed tool results are surfaced to the agent as an error body.
     */
    @Test
    void buildContinuationStatePackagesFailureAsErrorBody() {
        when(toolDispatcher.dispatch(eq("getProphetForecast"), anyString()))
                .thenReturn(AgentToolResult.failure("getProphetForecast", "service unavailable"));

        final SessionState state = adapter.buildContinuationState(
                returnControlPayload("inv-2", "getProphetForecast",
                        FunctionParameter.builder().name("productId").value("1").build()));

        assertThat(state.invocationId()).isEqualTo("inv-2");
        assertThat(state.returnControlInvocationResults().get(0)
                .functionResult().responseBody().get("TEXT").body())
                .contains("service unavailable");
    }

    /**
     * Verifies the loop stops after the maximum number of return-control turns.
     */
    @Test
    void invokeAgentStopsAfterMaxToolTurns() throws Exception {
        when(clientFactory.createAsyncClient(anyString())).thenReturn(asyncClient);
        final ReturnControlPayload payload = returnControlPayload("inv-loop", "getInventoryRisk");
        doReturn(new BedrockAgentRuntimeClientAdapter.AgentTurn("", payload))
                .when(adapter).executeTurn(eq(asyncClient), any(InvokeAgentRequest.class));
        when(toolDispatcher.dispatch(eq("getInventoryRisk"), anyString()))
                .thenReturn(AgentToolResult.success("getInventoryRisk", "[]"));

        final BedrockAgentInvokeResponse response =
                adapter.invokeAgent(new BedrockAgentInvokeRequest("질문", "session-1", null, null));

        assertThat(response.answer()).contains("초과");
        verify(adapter, times(BedrockAgentRuntimeClientAdapter.MAX_TOOL_TURNS + 1))
                .executeTurn(eq(asyncClient), any(InvokeAgentRequest.class));
    }

    /**
     * Verifies stream failures produce a safe error answer instead of propagating.
     */
    @Test
    void invokeAgentReturnsErrorAnswerWhenStreamFails() throws Exception {
        when(clientFactory.createAsyncClient(anyString())).thenReturn(asyncClient);
        doThrow(new IllegalStateException("stream broken"))
                .when(adapter).executeTurn(eq(asyncClient), any(InvokeAgentRequest.class));

        final BedrockAgentInvokeResponse response =
                adapter.invokeAgent(new BedrockAgentInvokeRequest("질문", "session-1", null, null));

        assertThat(response.answer()).contains("오류");
        assertThat(response.sessionId()).isEqualTo("session-1");
    }

    /**
     * Verifies the scope/safety guardrail is attached to the RAG (RetrieveAndGenerate) request
     * when both id and version are configured — closing the bypass on the free-text
     * {@code /api/v1/ai/bedrock/rag/query} path.
     */
    @Test
    void retrieveAndGenerateAttachesGuardrailWhenConfigured() {
        properties.setKnowledgeBaseId("KB123");
        properties.setModelId("model-arn");
        properties.setGuardrailId("gr-123");
        properties.setGuardrailVersion("DRAFT");
        when(clientFactory.createSyncClient(anyString())).thenReturn(syncClient);
        final ArgumentCaptor<RetrieveAndGenerateRequest> captor =
                ArgumentCaptor.forClass(RetrieveAndGenerateRequest.class);
        when(syncClient.retrieveAndGenerate(captor.capture())).thenReturn(
                RetrieveAndGenerateResponse.builder()
                        .output(RetrieveAndGenerateOutput.builder().text("조치 안내입니다.").build())
                        .build());

        final BedrockRagQueryResponse response = adapter.retrieveAndGenerate(
                new BedrockRagQueryRequest("재고 부족 시 어떻게 하나요?", "ALERT_TYPE", null));

        assertThat(response.answer()).isEqualTo("조치 안내입니다.");
        final var guardrail = captor.getValue().retrieveAndGenerateConfiguration()
                .knowledgeBaseConfiguration().generationConfiguration().guardrailConfiguration();
        assertThat(guardrail).isNotNull();
        assertThat(guardrail.guardrailId()).isEqualTo("gr-123");
        assertThat(guardrail.guardrailVersion()).isEqualTo("DRAFT");
    }

    /**
     * Verifies no generation/guardrail config is attached when the guardrail is not configured,
     * so the RAG request shape is unchanged for deployments without a guardrail.
     */
    @Test
    void retrieveAndGenerateOmitsGuardrailWhenNotConfigured() {
        properties.setKnowledgeBaseId("KB123");
        properties.setModelId("model-arn");
        when(clientFactory.createSyncClient(anyString())).thenReturn(syncClient);
        final ArgumentCaptor<RetrieveAndGenerateRequest> captor =
                ArgumentCaptor.forClass(RetrieveAndGenerateRequest.class);
        when(syncClient.retrieveAndGenerate(captor.capture())).thenReturn(
                RetrieveAndGenerateResponse.builder()
                        .output(RetrieveAndGenerateOutput.builder().text("answer").build())
                        .build());

        adapter.retrieveAndGenerate(new BedrockRagQueryRequest("질문", "ALERT_TYPE", null));

        assertThat(captor.getValue().retrieveAndGenerateConfiguration()
                .knowledgeBaseConfiguration().generationConfiguration()).isNull();
    }

    private ReturnControlPayload returnControlPayload(final String invocationId, final String function,
                                                      final FunctionParameter... parameters) {
        return ReturnControlPayload.builder()
                .invocationId(invocationId)
                .invocationInputs(InvocationInputMember.builder()
                        .functionInvocationInput(FunctionInvocationInput.builder()
                                .actionGroup("stockops-tools")
                                .function(function)
                                .parameters(List.of(parameters))
                                .build())
                        .build())
                .build();
    }
}
