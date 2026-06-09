package com.stockops.ai.bedrock.dto;

import java.util.List;

public record BedrockRagQueryResponse(
        String answer,
        List<String> citations,
        String sessionId) {
}
