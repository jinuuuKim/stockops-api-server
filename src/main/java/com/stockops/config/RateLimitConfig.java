package com.stockops.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Rate limiting configuration using Bucket4j with Redis-backed token buckets.
 * <p>
 * Provides three tiers of rate limiting:
 * <ul>
 *   <li>Authenticated users: 100 requests per minute per user</li>
 *   <li>Anonymous users: 10 requests per minute per IP</li>
 *   <li>Login endpoint: 5 requests per minute per IP (stricter)</li>
 * </ul>
 *
 * @author StockOps Team
 * @since 1.0
 * @see RateLimitFilter
 */
@Configuration
@ConditionalOnProperty(name = "stockops.ratelimit.enabled", havingValue = "true")
public class RateLimitConfig {

    /**
     * Capacity for authenticated users.
     */
    public static final long AUTHENTICATED_CAPACITY = 100L;

    /**
     * Capacity for anonymous users.
     */
    public static final long ANONYMOUS_CAPACITY = 10L;

    /**
     * Capacity for login endpoint.
     */
    public static final long LOGIN_CAPACITY = 5L;

    /**
     * Creates a Lettuce {@link RedisClient} for Bucket4j rate limiting.
     *
     * @param host Redis host from Spring configuration
     * @param port Redis port from Spring configuration
     * @return configured Redis client
     */
    @Bean(destroyMethod = "shutdown")
    public RedisClient bucket4jRedisClient(
            @Value("${spring.redis.host:localhost}") final String host,
            @Value("${spring.redis.port:6379}") final int port) {

        return RedisClient.create("redis://" + host + ":" + port);
    }

    /**
     * Creates a stateful Redis connection for Bucket4j with {@code String} keys
     * and {@code byte[]} values.
     *
     * @param client the Redis client managed by Spring
     * @return stateful Redis connection
     */
    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, byte[]> bucket4jRedisConnection(final RedisClient client) {
        return client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
    }

    /**
     * Creates the Lettuce-based proxy manager for distributed token buckets.
     * Buckets expire after a period slightly longer than the refill window
     * to prevent stale keys from accumulating in Redis.
     *
     * @param connection Redis connection for Bucket4j
     * @return configured proxy manager
     */
    @Bean
    public ProxyManager<String> rateLimitProxyManager(
            final StatefulRedisConnection<String, byte[]> connection) {

        return Bucket4jLettuce.casBasedBuilder(connection)
                .expirationAfterWrite(ExpirationAfterWriteStrategy
                        .basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(2)))
                .build();
    }

    /**
     * Bandwidth for authenticated users: 100 req/min with greedy refill.
     * Greedy refill allows bursts up to the full capacity.
     *
     * @return configured bandwidth
     */
    @Bean
    public Bandwidth authenticatedBandwidth() {
        return Bandwidth.builder()
                .capacity(AUTHENTICATED_CAPACITY)
                .refillGreedy(AUTHENTICATED_CAPACITY, Duration.ofMinutes(1))
                .build();
    }

    /**
     * Bandwidth for anonymous users: 10 req/min with greedy refill.
     *
     * @return configured bandwidth
     */
    @Bean
    public Bandwidth anonymousBandwidth() {
        return Bandwidth.builder()
                .capacity(ANONYMOUS_CAPACITY)
                .refillGreedy(ANONYMOUS_CAPACITY, Duration.ofMinutes(1))
                .build();
    }

    /**
     * Bandwidth for login endpoint: 5 req/min with greedy refill.
     *
     * @return configured bandwidth
     */
    @Bean
    public Bandwidth loginBandwidth() {
        return Bandwidth.builder()
                .capacity(LOGIN_CAPACITY)
                .refillGreedy(LOGIN_CAPACITY, Duration.ofMinutes(1))
                .build();
    }
}
