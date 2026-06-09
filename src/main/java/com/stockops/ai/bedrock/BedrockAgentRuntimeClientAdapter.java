package com.stockops.ai.bedrock;

import com.stockops.ai.bedrock.dto.BedrockAgentInvokeRequest;
import com.stockops.ai.bedrock.dto.BedrockAgentInvokeResponse;
import com.stockops.ai.bedrock.dto.BedrockRagQueryRequest;
import com.stockops.ai.bedrock.dto.BedrockRagQueryResponse;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrieveAndGenerateConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateInput;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateResponse;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateType;

@Component
public class BedrockAgentRuntimeClientAdapter {

    private static final Logger log = LoggerFactory.getLogger(BedrockAgentRuntimeClientAdapter.class);

    private final BedrockAiProperties properties;

    public BedrockAgentRuntimeClientAdapter(final BedrockAiProperties properties) {
        this.properties = properties;
    }

    public BedrockRagQueryResponse retrieveAndGenerate(final BedrockRagQueryRequest request) {
        if (!properties.isEnabled()
                || properties.getKnowledgeBaseId() == null
                || properties.getKnowledgeBaseId().isBlank()) {
            return new BedrockRagQueryResponse(
                    "Knowledge Base is not configured.",
                    List.of(),
                    null);
        }

        final String modelArn = properties.generationModelReference();
        try (BedrockAgentRuntimeClient client = BedrockAgentRuntimeClient.builder()
                .region(Region.of(properties.getRegion()))
                .build()) {
            final RetrieveAndGenerateRequest ragRequest = RetrieveAndGenerateRequest.builder()
                    .input(RetrieveAndGenerateInput.builder().text(request.question()).build())
                    .retrieveAndGenerateConfiguration(RetrieveAndGenerateConfiguration.builder()
                            .type(RetrieveAndGenerateType.KNOWLEDGE_BASE)
                            .knowledgeBaseConfiguration(KnowledgeBaseRetrieveAndGenerateConfiguration.builder()
                                    .knowledgeBaseId(properties.getKnowledgeBaseId())
                                    .modelArn(modelArn)
                                    .build())
                            .build())
                    .build();
            final RetrieveAndGenerateResponse response = client.retrieveAndGenerate(ragRequest);
            final List<String> citations = extractCitations(response);
            return new BedrockRagQueryResponse(
                    response.output().text(),
                    citations,
                    response.sessionId());
        } catch (final Exception e) {
            log.error("Bedrock RAG query failed: {}", e.getMessage(), e);
            return new BedrockRagQueryResponse(
                    "Knowledge Base 조회 중 오류가 발생했습니다.",
                    List.of(),
                    null);
        }
    }

    public BedrockAgentInvokeResponse invokeAgent(final BedrockAgentInvokeRequest request) {
        if (!properties.isEnabled()
                || properties.getAgentId() == null || properties.getAgentId().isBlank()
                || properties.getAgentAliasId() == null || properties.getAgentAliasId().isBlank()) {
            return new BedrockAgentInvokeResponse(
                    "Bedrock Agent is not configured.",
                    request.sessionId(),
                    false);
        }

        log.info("Bedrock Agent invocation: agentId={}, sessionId={}", properties.getAgentId(), request.sessionId());
        return new BedrockAgentInvokeResponse(
                "Bedrock Agent pilot mode — live invocation not yet implemented.",
                request.sessionId(),
                false);
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
