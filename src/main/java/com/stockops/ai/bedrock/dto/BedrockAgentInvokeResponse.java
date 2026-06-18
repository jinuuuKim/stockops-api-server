package com.stockops.ai.bedrock.dto;

/**
 * Assistant response.
 *
 * @param answer          final answer text
 * @param sessionId       conversation session id (echo back on the next turn for multi-turn context)
 * @param actionSuggested whether the assistant suggested an action
 * @param notice          optional system notice (e.g. history getting long, or session reset); null when none
 * @param sessionReset    true when the server cleared the stored history (client should drop its local history)
 */
public record BedrockAgentInvokeResponse(
        String answer,
        String sessionId,
        boolean actionSuggested,
        String notice,
        boolean sessionReset) {

    /** Convenience factory for callers that don't carry a history notice. */
    public static BedrockAgentInvokeResponse of(final String answer, final String sessionId, final boolean actionSuggested) {
        return new BedrockAgentInvokeResponse(answer, sessionId, actionSuggested, null, false);
    }
}
