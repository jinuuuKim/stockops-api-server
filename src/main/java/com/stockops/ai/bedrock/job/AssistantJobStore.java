package com.stockops.ai.bedrock.job;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed store for asynchronous assistant jobs, keyed by job id with a short TTL. Redis (not
 * in-memory) is required because polling requests may land on a different replica than the one that
 * ran the job.
 *
 * <p>All Redis failures are contained here (save/load log and degrade) so a transient Redis issue
 * never crashes the job lifecycle.
 *
 * @author StockOps Team
 * @since 2.7
 */
@Component
public class AssistantJobStore {

    private static final Logger log = LoggerFactory.getLogger(AssistantJobStore.class);
    private static final String KEY_PREFIX = "stockops:ai:assistant-job:";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public AssistantJobStore(final StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Persists (or overwrites) a job's state and refreshes its TTL.
     *
     * @param jobId job identifier
     * @param job   job state to store
     */
    public void save(final String jobId, final AssistantJob job) {
        if (jobId == null || jobId.isBlank() || job == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key(jobId), objectMapper.writeValueAsString(job), TTL);
        } catch (final Exception e) {
            log.warn("[AssistantJob] save failed for {}: {}", jobId, e.getMessage());
        }
    }

    /**
     * Loads a job's state.
     *
     * @param jobId job identifier
     * @return the stored job, or {@code null} when absent/expired or on a Redis error
     */
    public AssistantJob load(final String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return null;
        }
        try {
            final String json = redisTemplate.opsForValue().get(key(jobId));
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, AssistantJob.class);
        } catch (final Exception e) {
            log.warn("[AssistantJob] load failed for {}: {}", jobId, e.getMessage());
            return null;
        }
    }

    private String key(final String jobId) {
        return KEY_PREFIX + jobId;
    }
}
