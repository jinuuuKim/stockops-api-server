package com.stockops.ai.chat.dto;

public record AiChatResponse(
        String message,
        String provider,
        String serviceStatus,
        boolean fallbackUsed,
        String fallbackNotice,
        String serviceNotice,
        String fallbackReason) {
}
