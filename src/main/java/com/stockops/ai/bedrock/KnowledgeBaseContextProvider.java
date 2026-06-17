package com.stockops.ai.bedrock;

import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseQuery;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseVectorSearchConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveResponse;

/**
 * Pulls top-K ops-document snippets from the Bedrock Knowledge Base (vector search via the
 * {@code Retrieve} API) to inject as grounding context into the Converse tool-use conversation.
 *
 * <p>Gated on {@code stockops.ai.bedrock.knowledge-base-id}; returns {@code null} when the KB is
 * not configured or on any error, so the assistant degrades to tool-use-only without breaking.
 *
 * @author StockOps Team
 * @since 2.4
 */
@Component
public class KnowledgeBaseContextProvider {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseContextProvider.class);
    private static final int TOP_K = 4;
    private static final int MAX_SNIPPET_CHARS = 800;

    private final BedrockAiProperties properties;
    private final BedrockAgentRuntimeClientFactory clientFactory;

    public KnowledgeBaseContextProvider(final BedrockAiProperties properties,
                                        final BedrockAgentRuntimeClientFactory clientFactory) {
        this.properties = properties;
        this.clientFactory = clientFactory;
    }

    /**
     * Retrieves grounding snippets for the query.
     *
     * @param query user question
     * @return concatenated snippet text, or {@code null} when KB is unconfigured/unavailable
     */
    public String retrieveContext(final String query) {
        if (!properties.isEnabled()
                || properties.getKnowledgeBaseId() == null
                || properties.getKnowledgeBaseId().isBlank()
                || query == null || query.isBlank()) {
            return null;
        }
        try (BedrockAgentRuntimeClient client = clientFactory.createSyncClient(properties.getRegion())) {
            final RetrieveRequest request = RetrieveRequest.builder()
                    .knowledgeBaseId(properties.getKnowledgeBaseId())
                    .retrievalQuery(KnowledgeBaseQuery.builder().text(query).build())
                    .retrievalConfiguration(KnowledgeBaseRetrievalConfiguration.builder()
                            .vectorSearchConfiguration(KnowledgeBaseVectorSearchConfiguration.builder()
                                    .numberOfResults(TOP_K)
                                    .build())
                            .build())
                    .build();
            final RetrieveResponse response = client.retrieve(request);
            final String context = response.retrievalResults().stream()
                    .map(result -> result.content() != null ? result.content().text() : null)
                    .filter(text -> text != null && !text.isBlank())
                    .map(this::truncate)
                    .collect(Collectors.joining("\n---\n"));
            return context.isBlank() ? null : context;
        } catch (final Exception e) {
            log.warn("Knowledge Base retrieve failed (degrading to tool-use only): {}", e.getMessage());
            return null;
        }
    }

    private String truncate(final String text) {
        return text.length() > MAX_SNIPPET_CHARS ? text.substring(0, MAX_SNIPPET_CHARS) + "…" : text;
    }
}
