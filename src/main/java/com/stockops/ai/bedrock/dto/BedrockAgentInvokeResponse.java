package com.stockops.ai.bedrock.dto;

public record BedrockAgentInvokeResponse(
        String answer,
        String sessionId,
        boolean actionSuggested) {
}
