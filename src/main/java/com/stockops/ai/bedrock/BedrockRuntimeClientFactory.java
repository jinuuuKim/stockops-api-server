package com.stockops.ai.bedrock;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/**
 * Creates {@link BedrockRuntimeClient} instances. Extracted as a Spring bean so the
 * Converse tool-use loop can be unit-tested with a mocked client.
 *
 * @author StockOps Team
 * @since 2.4
 */
@Component
public class BedrockRuntimeClientFactory {

    private final BedrockCredentialsProvider credentialsProvider;

    public BedrockRuntimeClientFactory(final BedrockCredentialsProvider credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
    }

    /**
     * Creates a Bedrock runtime client for the given region.
     *
     * @param region AWS region id
     * @return runtime client; the caller owns closing it
     */
    public BedrockRuntimeClient create(final String region) {
        return BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build();
    }
}
