package com.stockops.ai.bedrock.chat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;

/**
 * A stored multi-turn chat session: an optional running summary of compacted history plus the
 * recent verbatim turns. Persisted as JSON in {@link ChatHistoryStore}.
 *
 * <p>Only final user/assistant text turns are stored — intermediate tool-use/tool-result blocks are
 * transient per request and never persisted, so replaying history needs no tool-result pairing.
 *
 * @author StockOps Team
 * @since 2.6
 */
public class ChatSession {

    /** One stored turn. {@code role} is {@code "USER"} or {@code "ASSISTANT"}. */
    public record Turn(String role, String text) {
        public static final String USER = "USER";
        public static final String ASSISTANT = "ASSISTANT";
    }

    private String summary;
    private List<Turn> turns = new ArrayList<>();

    public String getSummary() { return summary; }
    public void setSummary(final String summary) { this.summary = summary; }
    public List<Turn> getTurns() { return turns; }
    public void setTurns(final List<Turn> turns) { this.turns = turns != null ? turns : new ArrayList<>(); }

    @JsonIgnore
    public boolean isEmpty() {
        return (summary == null || summary.isBlank()) && turns.isEmpty();
    }

    public void addTurn(final String role, final String text) {
        turns.add(new Turn(role, text == null ? "" : text));
    }

    /** Total character length of summary + all turn texts — a cheap token proxy for size guards. */
    @JsonIgnore
    public int charSize() {
        int size = summary == null ? 0 : summary.length();
        for (final Turn turn : turns) {
            if (turn.text() != null) {
                size += turn.text().length();
            }
        }
        return size;
    }
}
