package com.stockops.ai.bedrock.dto;

import jakarta.validation.constraints.NotBlank;

public record BedrockAgentInvokeRequest(
        @NotBlank String message,
        String sessionId) {
}
