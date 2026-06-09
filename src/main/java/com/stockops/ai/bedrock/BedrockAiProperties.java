package com.stockops.ai.bedrock;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "stockops.ai.bedrock")
public class BedrockAiProperties {

    private boolean enabled;
    private String region = "ap-northeast-2";
    private String modelId = "";
    private String inferenceProfileArn = "";
    private String knowledgeBaseId = "";
    private String agentId = "";
    private String agentAliasId = "";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(20);
    private int maxOutputTokens = 1200;
    private double temperature = 0.2;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
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

    public String generationModelReference() {
        return inferenceProfileArn == null || inferenceProfileArn.isBlank() ? modelId : inferenceProfileArn;
    }
}
