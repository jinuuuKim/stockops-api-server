package com.stockops.ai.bedrock.chat;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Tuning for the assistant's multi-turn chat history (Redis-backed).
 *
 * <p>Size is measured as the total character length of the stored summary plus every turn's text —
 * a cheap proxy for token usage. Three escalating thresholds bound how large a single conversation
 * can grow: a soft warning, a Bedrock-summarization compaction, and a hard reset.
 *
 * @author StockOps Team
 * @since 2.6
 */
@Component
@ConfigurationProperties(prefix = "stockops.ai.chat-history")
public class ChatHistoryProperties {

    /** Master switch; when false the assistant runs single-turn (no history). */
    private boolean enabled = true;

    /** Idle TTL — a session expires this long after its last message. */
    private Duration ttl = Duration.ofMinutes(30);

    /** Soft warn: a notice is attached but the conversation continues unchanged. */
    private int warnChars = 6000;

    /** Compaction: older turns are summarized via Bedrock and replaced by the summary. */
    private int compactChars = 12000;

    /** Hard reset: the whole session is cleared and the turn starts fresh. */
    private int hardResetChars = 24000;

    /** Number of most-recent turns kept verbatim when compacting. */
    private int keepRecentTurns = 4;

    /** Absolute cap on stored turns regardless of size (belt-and-suspenders). */
    private int maxStoredTurns = 40;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(final boolean enabled) { this.enabled = enabled; }
    public Duration getTtl() { return ttl; }
    public void setTtl(final Duration ttl) { this.ttl = ttl; }
    public int getWarnChars() { return warnChars; }
    public void setWarnChars(final int warnChars) { this.warnChars = warnChars; }
    public int getCompactChars() { return compactChars; }
    public void setCompactChars(final int compactChars) { this.compactChars = compactChars; }
    public int getHardResetChars() { return hardResetChars; }
    public void setHardResetChars(final int hardResetChars) { this.hardResetChars = hardResetChars; }
    public int getKeepRecentTurns() { return keepRecentTurns; }
    public void setKeepRecentTurns(final int keepRecentTurns) { this.keepRecentTurns = keepRecentTurns; }
    public int getMaxStoredTurns() { return maxStoredTurns; }
    public void setMaxStoredTurns(final int maxStoredTurns) { this.maxStoredTurns = maxStoredTurns; }
}
