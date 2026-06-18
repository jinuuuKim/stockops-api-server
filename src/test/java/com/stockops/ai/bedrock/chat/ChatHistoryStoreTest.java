package com.stockops.ai.bedrock.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Verifies {@link ChatHistoryStore} round-trips a session through JSON — a regression test for the
 * deserialization bug where the {@code isEmpty()} getter emitted an unknown {@code "empty"} property
 * that the strict default mapper rejected on load, silently dropping all history.
 *
 * @author StockOps Team
 * @since 2.6
 */
@ExtendWith(MockitoExtension.class)
class ChatHistoryStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private ChatHistoryStore store;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        store = new ChatHistoryStore(redisTemplate, new ChatHistoryProperties());
    }

    @Test
    void saveThenLoad_roundTripsTurnsAndSummary() {
        final ChatSession session = new ChatSession();
        session.setSummary("이전 요약");
        session.addTurn(ChatSession.Turn.USER, "야채교자 재고?");
        session.addTurn(ChatSession.Turn.ASSISTANT, "1,992개입니다.");

        final ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        store.save("sess-1", session);
        verify(valueOps).set(eq("stockops:ai:chat:sess-1"), json.capture(), any(Duration.class));

        when(valueOps.get("stockops:ai:chat:sess-1")).thenReturn(json.getValue());
        final ChatSession loaded = store.load("sess-1");

        assertThat(loaded.isEmpty()).isFalse();
        assertThat(loaded.getSummary()).isEqualTo("이전 요약");
        assertThat(loaded.getTurns()).hasSize(2);
        assertThat(loaded.getTurns().get(0).role()).isEqualTo(ChatSession.Turn.USER);
        assertThat(loaded.getTurns().get(0).text()).isEqualTo("야채교자 재고?");
        assertThat(loaded.getTurns().get(1).text()).isEqualTo("1,992개입니다.");
    }

    @Test
    void load_missingKey_returnsEmptySession() {
        when(valueOps.get(any())).thenReturn(null);
        assertThat(store.load("nope").isEmpty()).isTrue();
    }
}
