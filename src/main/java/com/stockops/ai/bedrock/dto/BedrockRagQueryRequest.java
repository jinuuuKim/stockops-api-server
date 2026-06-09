package com.stockops.ai.bedrock.dto;

import jakarta.validation.constraints.NotBlank;

public record BedrockRagQueryRequest(
        @NotBlank String question,
        String scopeType,
        Long scopeId) {
}
