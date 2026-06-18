package com.stockops.ai.bedrock.chat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed store for assistant chat sessions, keyed by session id with an idle TTL so inactive
 * conversations expire on their own.
 *
 * <p>All Redis failures are contained here: load returns an empty session, save/clear log and
 * continue. The assistant must never fail because history is unavailable.
 *
 * @author StockOps Team
 * @since 2.6
 */
@Component
@ConditionalOnProperty(name = "stockops.redis.enabled", havingValue = "true", matchIfMissing = true)
public class ChatHistoryStore {

    private static final Logger log = LoggerFactory.getLogger(ChatHistoryStore.class);
    private static final String KEY_PREFIX = "stockops:ai:chat:";

    private final StringRedisTemplate redisTemplate;
    private final ChatHistoryProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public ChatHistoryStore(final StringRedisTemplate redisTemplate, final ChatHistoryProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * Loads a session, returning an empty one when absent or on any error.
     *
     * @param sessionId session identifier
     * @return the stored session, or a fresh empty session
     */
    public ChatSession load(final String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return new ChatSession();
        }
        try {
            final String json = redisTemplate.opsForValue().get(key(sessionId));
            if (json == null || json.isBlank()) {
                return new ChatSession();
            }
            return objectMapper.readValue(json, ChatSession.class);
        } catch (final Exception e) {
            log.warn("[ChatHistory] load failed for session {} (degrading to empty): {}", sessionId, e.getMessage());
            return new ChatSession();
        }
    }

    /**
     * Saves a session, trimming to the configured turn cap and refreshing the idle TTL.
     *
     * @param sessionId session identifier
     * @param session   session to persist
     */
    public void save(final String sessionId, final ChatSession session) {
        if (sessionId == null || sessionId.isBlank() || session == null) {
            return;
        }
        try {
            final int cap = Math.max(2, properties.getMaxStoredTurns());
            if (session.getTurns().size() > cap) {
                session.setTurns(new ArrayList<>(session.getTurns().subList(session.getTurns().size() - cap, session.getTurns().size())));
            }
            final String json = objectMapper.writeValueAsString(session);
            redisTemplate.opsForValue().set(key(sessionId), json, properties.getTtl());
        } catch (final Exception e) {
            log.warn("[ChatHistory] save failed for session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Clears a session (used on hard reset).
     *
     * @param sessionId session identifier
     */
    public void clear(final String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            redisTemplate.delete(key(sessionId));
        } catch (final Exception e) {
            log.warn("[ChatHistory] clear failed for session {}: {}", sessionId, e.getMessage());
        }
    }

    private String key(final String sessionId) {
        return KEY_PREFIX + sessionId;
    }
}
