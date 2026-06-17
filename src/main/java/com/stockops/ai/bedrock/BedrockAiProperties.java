package com.stockops.ai.bedrock;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "stockops.ai.bedrock")
public class BedrockAiProperties {

    private boolean enabled;
    private String region = "ap-northeast-2";
    private String accessKeyId = "";
    private String secretAccessKey = "";
    private String modelId = "";
    private String inferenceProfileArn = "";
    private String knowledgeBaseId = "";
    private String agentId = "";
    private String agentAliasId = "";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(20);
    private int maxOutputTokens = 1200;
    private double temperature = 0.2;
    private String guardrailId = "";
    private String guardrailVersion = "";
    private int maxToolTurns = 5;
    private String systemPrompt = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getAccessKeyId() { return accessKeyId; }
    public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
    public String getSecretAccessKey() { return secretAccessKey; }
    public void setSecretAccessKey(String secretAccessKey) { this.secretAccessKey = secretAccessKey; }
    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public String getInferenceProfileArn() { return inferenceProfileArn; }
    public void setInferenceProfileArn(String inferenceProfileArn) { this.inferenceProfileArn = inferenceProfileArn; }
    public String getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(String knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getAgentAliasId() { return agentAliasId; }
    public void setAgentAliasId(String agentAliasId) { this.agentAliasId = agentAliasId; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public String getGuardrailId() { return guardrailId; }
    public void setGuardrailId(String guardrailId) { this.guardrailId = guardrailId; }
    public String getGuardrailVersion() { return guardrailVersion; }
    public void setGuardrailVersion(String guardrailVersion) { this.guardrailVersion = guardrailVersion; }
    public int getMaxToolTurns() { return maxToolTurns; }
    public void setMaxToolTurns(int maxToolTurns) { this.maxToolTurns = maxToolTurns; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public String generationModelReference() {
        return inferenceProfileArn == null || inferenceProfileArn.isBlank() ? modelId : inferenceProfileArn;
    }

    /**
     * Returns whether explicit static credentials are configured for Bedrock.
     *
     * <p>When true, Bedrock clients use these keys instead of the default credential chain
     * (IRSA / env vars), so Bedrock can target a different AWS account than the rest of the
     * service (e.g. SQS ingestion via IRSA in the deployment account, Bedrock via a personal
     * account where model access is enabled). When false, Bedrock falls back to the default
     * chain.
     *
     * @return true when both access key id and secret access key are set
     */
    public boolean hasStaticCredentials() {
        return accessKeyId != null && !accessKeyId.isBlank()
                && secretAccessKey != null && !secretAccessKey.isBlank();
    }

    /**
     * Returns whether a Bedrock Guardrail is configured (both id and version present).
     *
     * @return true when guardrailConfig should be attached to Converse requests
     */
    public boolean hasGuardrail() {
        return guardrailId != null && !guardrailId.isBlank()
                && guardrailVersion != null && !guardrailVersion.isBlank();
    }
}
