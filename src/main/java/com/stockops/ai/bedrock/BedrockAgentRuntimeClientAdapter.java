package com.stockops.ai.bedrock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stockops.ai.bedrock.agent.AgentToolDispatcher;
import com.stockops.ai.bedrock.agent.AgentToolResult;
import com.stockops.ai.bedrock.dto.BedrockAgentInvokeRequest;
import com.stockops.ai.bedrock.dto.BedrockAgentInvokeResponse;
import com.stockops.ai.bedrock.dto.BedrockRagQueryRequest;
import com.stockops.ai.bedrock.dto.BedrockRagQueryResponse;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.Observed;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.ContentBody;
import software.amazon.awssdk.services.bedrockagentruntime.model.FunctionInvocationInput;
import software.amazon.awssdk.services.bedrockagentruntime.model.FunctionResult;
import software.amazon.awssdk.services.bedrockagentruntime.model.GenerationConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.GuardrailConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvocationInputMember;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvocationResultMember;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeAgentRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeAgentResponseHandler;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrieveAndGenerateConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateInput;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateResponse;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateType;
import software.amazon.awssdk.services.bedrockagentruntime.model.ReturnControlPayload;
import software.amazon.awssdk.services.bedrockagentruntime.model.SessionState;

/**
 * Bedrock Agent runtime adapter.
 *
 * <p>{@link #invokeAgent(BedrockAgentInvokeRequest)} runs the full return-control loop:
 * the agent's streamed answer chunks are collected, and whenever the agent emits a
 * {@code returnControl} event the requested tool is executed through
 * {@link AgentToolDispatcher} and the result is sent back via
 * {@link InvokeAgentRequest#sessionState()} until the agent produces its final answer.
 * Spring stays the control plane: Bedrock only ever sees allowlisted tool results.
 *
 * @author StockOps Team
 * @since 2.0
 */
@Component
public class BedrockAgentRuntimeClientAdapter {

    private static final Logger log = LoggerFactory.getLogger(BedrockAgentRuntimeClientAdapter.class);

    /** Upper bound on agent return-control round trips per invocation. */
    static final int MAX_TOOL_TURNS = 5;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final BedrockAiProperties properties;
    private final AgentToolDispatcher toolDispatcher;
    private final BedrockAgentRuntimeClientFactory clientFactory;
    private final ObservationRegistry observationRegistry;

    public BedrockAgentRuntimeClientAdapter(final BedrockAiProperties properties,
                                            final AgentToolDispatcher toolDispatcher,
                                            final BedrockAgentRuntimeClientFactory clientFactory,
                                            final ObservationRegistry observationRegistry) {
        this.properties = properties;
        this.toolDispatcher = toolDispatcher;
        this.clientFactory = clientFactory;
        this.observationRegistry = observationRegistry;
    }

    @Observed(name = "ai.bedrock.rag_retrieve_generate", contextualName = "bedrock-rag-retrieve-generate")
    public BedrockRagQueryResponse retrieveAndGenerate(final BedrockRagQueryRequest request) {
        if (!properties.isEnabled()
                || properties.getKnowledgeBaseId() == null
                || properties.getKnowledgeBaseId().isBlank()) {
            tagCurrentObservation("rag.outcome", "not_configured");
            return new BedrockRagQueryResponse(
                    "Knowledge Base is not configured.",
                    List.of(),
                    null);
        }

        // Records whether the scope/safety guardrail is active on this free-text RAG call so the
        // trace shows it was not silently bypassed. Tagged before the call so it is present even
        // when the request fails.
        tagCurrentObservation("guardrail.applied", properties.hasGuardrail());

        final String modelArn = properties.generationModelReference();
        try (BedrockAgentRuntimeClient client = clientFactory.createSyncClient(properties.getRegion())) {
            // Scope/safety guardrail also covers this RAG path, which accepts free-text user
            // questions (e.g. /api/v1/ai/bedrock/rag/query). Without it, users could bypass the
            // guardrail attached to the Converse assistant. Attached only when both id+version are set.
            final KnowledgeBaseRetrieveAndGenerateConfiguration.Builder knowledgeBaseConfig =
                    KnowledgeBaseRetrieveAndGenerateConfiguration.builder()
                            .knowledgeBaseId(properties.getKnowledgeBaseId())
                            .modelArn(modelArn);
            if (properties.hasGuardrail()) {
                knowledgeBaseConfig.generationConfiguration(GenerationConfiguration.builder()
                        .guardrailConfiguration(GuardrailConfiguration.builder()
                                .guardrailId(properties.getGuardrailId())
                                .guardrailVersion(properties.getGuardrailVersion())
                                .build())
                        .build());
            }
            final RetrieveAndGenerateRequest ragRequest = RetrieveAndGenerateRequest.builder()
                    .input(RetrieveAndGenerateInput.builder().text(request.question()).build())
                    .retrieveAndGenerateConfiguration(RetrieveAndGenerateConfiguration.builder()
                            .type(RetrieveAndGenerateType.KNOWLEDGE_BASE)
                            .knowledgeBaseConfiguration(knowledgeBaseConfig.build())
                            .build())
                    .build();
            final RetrieveAndGenerateResponse response = client.retrieveAndGenerate(ragRequest);
            final List<String> citations = extractCitations(response);
            tagCurrentObservation("rag.outcome", "success");
            tagCurrentObservation("rag.citation_count", citations.size());
            return new BedrockRagQueryResponse(
                    response.output().text(),
                    citations,
                    response.sessionId());
        } catch (final Exception e) {
            tagCurrentObservation("rag.outcome", "error");
            log.error("Bedrock RAG query failed: {}", e.getMessage(), e);
            return new BedrockRagQueryResponse(
                    "Knowledge Base 조회 중 오류가 발생했습니다.",
                    List.of(),
                    null);
        }
    }

    /**
     * Attaches a key/value tag to the current observation (the {@code @Observed} span), when one is
     * active. Mirrors the pattern in {@code AiForecastClient}: no-op outside a trace context
     * (e.g. unit tests without AOP weaving) so callers stay clean.
     *
     * @param key tag key
     * @param value tag value; ignored when null
     */
    private void tagCurrentObservation(final String key, final Object value) {
        final Observation current = observationRegistry.getCurrentObservation();
        if (current != null && value != null) {
            current.highCardinalityKeyValue(key, String.valueOf(value));
        }
    }

    /**
     * Invokes the Bedrock Agent and drives the return-control loop until a final answer.
     *
     * <p>Each turn streams events from {@code invokeAgent}. Chunk events accumulate the
     * agent's natural-language answer. A {@code returnControl} event suspends the agent:
     * the requested tool calls are dispatched to {@link AgentToolDispatcher} and the results
     * are submitted back through {@code sessionState.returnControlInvocationResults}, after
     * which the loop continues. The loop is bounded by {@link #MAX_TOOL_TURNS}.
     *
     * @param request agent invocation request
     * @return final agent answer with the session id used
     */
    public BedrockAgentInvokeResponse invokeAgent(final BedrockAgentInvokeRequest request) {
        if (!properties.isEnabled()
                || properties.getAgentId() == null || properties.getAgentId().isBlank()
                || properties.getAgentAliasId() == null || properties.getAgentAliasId().isBlank()) {
            return new BedrockAgentInvokeResponse(
                    "Bedrock Agent is not configured.",
                    request.sessionId(),
                    false);
        }

        final String sessionId = StringUtils.hasText(request.sessionId())
                ? request.sessionId()
                : UUID.randomUUID().toString();
        log.info("Bedrock Agent invocation: agentId={}, sessionId={}", properties.getAgentId(), sessionId);

        try (BedrockAgentRuntimeAsyncClient client = clientFactory.createAsyncClient(properties.getRegion())) {
            InvokeAgentRequest invokeRequest = baseRequest(sessionId)
                    .inputText(request.message())
                    .build();
            final StringBuilder answer = new StringBuilder();

            for (int turn = 0; turn <= MAX_TOOL_TURNS; turn++) {
                final AgentTurn result = executeTurn(client, invokeRequest);
                answer.append(result.text());
                if (result.returnControl() == null) {
                    return new BedrockAgentInvokeResponse(answer.toString(), sessionId, false);
                }
                if (turn == MAX_TOOL_TURNS) {
                    break;
                }
                invokeRequest = baseRequest(sessionId)
                        .sessionState(buildContinuationState(result.returnControl()))
                        .build();
            }

            log.warn("Bedrock Agent exceeded {} return-control turns for sessionId={}", MAX_TOOL_TURNS, sessionId);
            return new BedrockAgentInvokeResponse(
                    answer.isEmpty()
                            ? "에이전트가 허용된 도구 호출 횟수를 초과하여 응답을 완료하지 못했습니다."
                            : answer.toString(),
                    sessionId,
                    false);
        } catch (final Exception e) {
            log.error("Bedrock Agent invocation failed: {}", e.getMessage(), e);
            return new BedrockAgentInvokeResponse(
                    "Bedrock Agent 호출 중 오류가 발생했습니다.",
                    sessionId,
                    false);
        }
    }

    /**
     * Executes a single streamed agent turn, collecting answer chunks and any
     * return-control event. Package-visible so the loop logic can be unit-tested
     * without a live event stream.
     *
     * @param client async Bedrock Agent runtime client
     * @param request invoke request for this turn
     * @return collected turn outcome
     * @throws Exception when the stream fails or times out
     */
    AgentTurn executeTurn(final BedrockAgentRuntimeAsyncClient client,
                          final InvokeAgentRequest request) throws Exception {
        final StringBuilder text = new StringBuilder();
        final AtomicReference<ReturnControlPayload> returnControl = new AtomicReference<>();

        final InvokeAgentResponseHandler handler = InvokeAgentResponseHandler.builder()
                .subscriber(InvokeAgentResponseHandler.Visitor.builder()
                        .onChunk(chunk -> {
                            if (chunk.bytes() != null) {
                                text.append(chunk.bytes().asUtf8String());
                            }
                        })
                        .onReturnControl(returnControl::set)
                        .build())
                .build();

        client.invokeAgent(request, handler)
                .get(properties.getReadTimeout().toMillis(), TimeUnit.MILLISECONDS);
        return new AgentTurn(text.toString(), returnControl.get());
    }

    /**
     * Dispatches every tool call requested by a return-control event and packages the
     * results as session state for the next agent turn.
     *
     * @param payload return-control payload from the agent
     * @return session state carrying the tool results
     */
    SessionState buildContinuationState(final ReturnControlPayload payload) {
        final List<InvocationResultMember> results = new ArrayList<>();
        for (final InvocationInputMember member : payload.invocationInputs()) {
            final FunctionInvocationInput function = member.functionInvocationInput();
            if (function == null) {
                log.warn("[Agent] Unsupported return-control input type (API invocation): {}", member);
                continue;
            }
            final String toolName = function.function();
            final String inputJson = toInputJson(function);
            final AgentToolResult result = toolDispatcher.dispatch(toolName, inputJson);
            results.add(InvocationResultMember.builder()
                    .functionResult(FunctionResult.builder()
                            .actionGroup(function.actionGroup())
                            .function(toolName)
                            .responseBody(Map.of("TEXT", ContentBody.builder()
                                    .body(toResponseBody(result))
                                    .build()))
                            .build())
                    .build());
        }
        return SessionState.builder()
                .invocationId(payload.invocationId())
                .returnControlInvocationResults(results)
                .build();
    }

    /**
     * Converts the agent's function parameters into the JSON object shape the
     * {@link AgentToolDispatcher} expects.
     *
     * @param function function invocation input
     * @return JSON object string of parameter name/value pairs
     */
    String toInputJson(final FunctionInvocationInput function) {
        final ObjectNode node = JSON.createObjectNode();
        if (function.parameters() != null) {
            function.parameters().forEach(parameter -> node.put(parameter.name(), parameter.value()));
        }
        return node.toString();
    }

    private String toResponseBody(final AgentToolResult result) {
        if (result.success()) {
            return result.resultJson() != null ? result.resultJson() : "{}";
        }
        final ObjectNode error = JSON.createObjectNode();
        error.put("error", result.errorMessage() != null ? result.errorMessage() : "tool execution failed");
        return error.toString();
    }

    private InvokeAgentRequest.Builder baseRequest(final String sessionId) {
        return InvokeAgentRequest.builder()
                .agentId(properties.getAgentId())
                .agentAliasId(properties.getAgentAliasId())
                .sessionId(sessionId);
    }

    /**
     * Outcome of one streamed agent turn.
     *
     * @param text concatenated answer chunks
     * @param returnControl return-control event when the agent requested a tool, else {@code null}
     */
    record AgentTurn(String text, ReturnControlPayload returnControl) {
    }

    private List<String> extractCitations(final RetrieveAndGenerateResponse response) {
        final List<String> citations = new ArrayList<>();
        if (response.citations() != null) {
            for (final var citation : response.citations()) {
                if (citation.retrievedReferences() != null) {
                    for (final var ref : citation.retrievedReferences()) {
                        if (ref.location() != null && ref.location().s3Location() != null) {
                            citations.add(ref.location().s3Location().uri());
                        }
                    }
                }
            }
        }
        return citations;
    }
}
