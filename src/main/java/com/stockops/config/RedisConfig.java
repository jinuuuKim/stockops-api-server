package com.stockops.config;

import java.time.Duration;

import org.springframework.cache.CacheManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Redis configuration for StockOps.
 * Configures RedisTemplate and CacheManager with JSON serialization.
 *
 * @author StockOps Team
 * @since 1.0
 */
@Configuration
@ConditionalOnProperty(name = "stockops.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisConfig {

    /**
     * Creates a RedisTemplate configured with String keys and JSON values.
     * Uses GenericJackson2JsonRedisSerializer for automatic object serialization.
     *
     * @param connectionFactory Redis connection factory (auto-configured by Spring)
     * @return configured RedisTemplate instance
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        GenericJackson2JsonRedisSerializer jsonSerializer = redisJsonSerializer();
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Creates a CacheManager using Redis with per-cache TTL configuration.
     * <ul>
     *   <li>dashboard::summary — 60s (dashboard aggregates change frequently)</li>
     *   <li>inventory — 120s (inventory status is read-heavy, mutation-evicted)</li>
     *   <li>center::inventory — 120s (center aggregation is derived from inventory)</li>
     *   <li>ai::recommendations — 300s (recommendations are regenerated daily)</li>
     *   <li>ai::recommendation-explanation — 1h (LLM explanations are stable; evicted on approval)</li>
     *   <li>ai::ops-summary — 24h (daily batch pre-warms this cache each morning)</li>
     *   <li>default — 30m (fallback for any unlisted cache)</li>
     * </ul>
     *
     * @param connectionFactory Redis connection factory
     * @return configured CacheManager
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer jsonSerializer = redisJsonSerializer();

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .disableCachingNullValues();

        RedisCacheConfiguration dashboardConfig = defaultConfig.entryTtl(Duration.ofSeconds(60));
        RedisCacheConfiguration inventoryConfig = defaultConfig.entryTtl(Duration.ofSeconds(120));
        RedisCacheConfiguration centerConfig = defaultConfig.entryTtl(Duration.ofSeconds(120));
        RedisCacheConfiguration aiConfig = defaultConfig.entryTtl(Duration.ofSeconds(300));
        RedisCacheConfiguration aiExplanationConfig = defaultConfig.entryTtl(Duration.ofHours(1));
        RedisCacheConfiguration aiOpsSummaryConfig = defaultConfig.entryTtl(Duration.ofHours(24));
        RedisCacheConfiguration aiEventGuidanceConfig = defaultConfig.entryTtl(Duration.ofHours(24));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("dashboard::summary", dashboardConfig)
                .withCacheConfiguration("inventory", inventoryConfig)
                .withCacheConfiguration("center::inventory", centerConfig)
                .withCacheConfiguration("ai::recommendations", aiConfig)
                .withCacheConfiguration("ai::recommendation-explanation", aiExplanationConfig)
                .withCacheConfiguration("ai::ops-summary", aiOpsSummaryConfig)
                .withCacheConfiguration("ai::event-guidance", aiEventGuidanceConfig)
                .build();
    }

    private GenericJackson2JsonRedisSerializer redisJsonSerializer() {
        return new GenericJackson2JsonRedisSerializer()
                .configure(this::configureRedisObjectMapper);
    }

    private void configureRedisObjectMapper(final ObjectMapper objectMapper) {
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
