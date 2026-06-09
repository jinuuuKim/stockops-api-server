package com.stockops.ai.provider;

public record AiGenerationResponse(
        String text,
        String provider,
        String modelId,
        AiServiceStatus serviceStatus,
        boolean fallbackUsed,
        String fallbackReason,
        String fallbackNotice,
        String serviceNotice) {
}
