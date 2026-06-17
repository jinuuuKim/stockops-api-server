package com.stockops.ai.bedrock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

/**
 * Dedicated credentials provider for Bedrock clients only.
 *
 * <p>This exists so Bedrock can authenticate against a <em>different</em> AWS account than the
 * rest of the service. SQS ingestion and other AWS calls keep using the SDK default credential
 * chain (IRSA in the deployment account); Bedrock uses these explicit keys when configured.
 *
 * <p><strong>Why a dedicated provider rather than {@code AWS_ACCESS_KEY_ID} env vars:</strong>
 * the SDK default chain picks up the magic {@code AWS_ACCESS_KEY_ID}/{@code AWS_SECRET_ACCESS_KEY}
 * environment variables ahead of IRSA, so setting them globally hijacks <em>every</em> AWS client
 * in the JVM — including the cross-account SQS subscriber, which then gets a 403. Scoping the
 * Bedrock credentials to this provider (fed by separately-named env vars) keeps the two paths
 * isolated.
 *
 * <p>When {@link BedrockAiProperties#hasStaticCredentials()} is false, this delegates to the
 * default chain so the bean stays inert (e.g. local dev, or a future migration where Bedrock
 * shares the deployment account via IRSA).
 *
 * @author StockOps Team
 * @since 2.5
 */
@Component
public class BedrockCredentialsProvider implements AwsCredentialsProvider {

    private static final Logger log = LoggerFactory.getLogger(BedrockCredentialsProvider.class);

    private final AwsCredentialsProvider delegate;

    public BedrockCredentialsProvider(final BedrockAiProperties properties) {
        if (properties.hasStaticCredentials()) {
            this.delegate = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.getAccessKeyId(), properties.getSecretAccessKey()));
            log.info("Bedrock using dedicated static credentials (accessKeyId={}…), isolated from the default chain",
                    properties.getAccessKeyId().substring(0, Math.min(4, properties.getAccessKeyId().length())));
        } else {
            this.delegate = DefaultCredentialsProvider.create();
            log.info("Bedrock using default credential chain (no dedicated static credentials configured)");
        }
    }

    @Override
    public AwsCredentials resolveCredentials() {
        return delegate.resolveCredentials();
    }
}
