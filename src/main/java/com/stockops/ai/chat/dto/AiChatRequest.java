package com.stockops.ai.chat.dto;

import jakarta.validation.constraints.NotBlank;

public record AiChatRequest(
        @NotBlank String message,
        String scopeType,
        Long scopeId) {
}
