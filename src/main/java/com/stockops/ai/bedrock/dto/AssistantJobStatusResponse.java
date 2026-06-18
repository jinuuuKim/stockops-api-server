package com.stockops.ai.bedrock.dto;

/**
 * Polling response for an asynchronous assistant job.
 *
 * @param jobId  job identifier
 * @param status one of {@code PENDING}, {@code DONE}, {@code ERROR}
 * @param result the assistant response, present only when {@code status == DONE}
 * @param error  failure message, present only when {@code status == ERROR}
 */
public record AssistantJobStatusResponse(
        String jobId,
        String status,
        BedrockAgentInvokeResponse result,
        String error) {
}
