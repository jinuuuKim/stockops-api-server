package com.stockops.ai.bedrock;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;

/**
 * Creates Bedrock Agent runtime clients. Extracted as a Spring bean so the
 * adapter's agent loop can be unit-tested with a mocked client.
 *
 * @author StockOps Team
 * @since 2.0
 */
@Component
public class BedrockAgentRuntimeClientFactory {

    private final BedrockCredentialsProvider credentialsProvider;

    public BedrockAgentRuntimeClientFactory(final BedrockCredentialsProvider credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
    }

    /**
     * Creates an async Bedrock Agent runtime client for the given region.
     * The async client is required because {@code invokeAgent} responses stream events.
     *
     * @param region AWS region id
     * @return async client; callers own closing it
     */
    public BedrockAgentRuntimeAsyncClient createAsyncClient(final String region) {
        return BedrockAgentRuntimeAsyncClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build();
    }

    /**
     * Creates a synchronous Bedrock Agent runtime client for the given region.
     * Used by the {@code Retrieve} and {@code RetrieveAndGenerate} (RAG) call paths.
     *
     * @param region AWS region id
     * @return sync client; callers own closing it
     */
    public BedrockAgentRuntimeClient createSyncClient(final String region) {
        return BedrockAgentRuntimeClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build();
    }
}
