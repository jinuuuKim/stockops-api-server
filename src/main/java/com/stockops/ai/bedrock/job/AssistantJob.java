package com.stockops.ai.bedrock.job;

import com.stockops.ai.bedrock.dto.BedrockAgentInvokeResponse;

/**
 * State of an asynchronous assistant job, persisted in Redis so the client can poll for completion
 * instead of holding a single long-lived request open (which fought the 30s axios / proxy timeouts).
 *
 * @param status one of {@link #PENDING}, {@link #DONE}, {@link #ERROR}
 * @param result the assistant response, present only when {@code status == DONE}
 * @param error  failure message, present only when {@code status == ERROR}
 * @author StockOps Team
 * @since 2.7
 */
public record AssistantJob(String status, BedrockAgentInvokeResponse result, String error) {

    public static final String PENDING = "PENDING";
    public static final String DONE = "DONE";
    public static final String ERROR = "ERROR";

    public static AssistantJob pending() {
        return new AssistantJob(PENDING, null, null);
    }

    public static AssistantJob done(final BedrockAgentInvokeResponse result) {
        return new AssistantJob(DONE, result, null);
    }

    public static AssistantJob failed(final String error) {
        return new AssistantJob(ERROR, null, error);
    }
}
