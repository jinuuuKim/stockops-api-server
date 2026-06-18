package com.stockops.ai.bedrock.dto;

/**
 * Response to creating an asynchronous assistant job.
 *
 * @param jobId identifier the client polls for the result
 */
public record AssistantJobCreatedResponse(String jobId) {
}
