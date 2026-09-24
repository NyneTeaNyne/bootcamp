package com.mentoring.bootcamp.ordermanager.api.config;

import com.mentoring.bootcamp.ordermanager.api.models.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

/**
 * Orders are cached in Valkey (Redis protocol), stored as readable JSON.
 * <ul>
 *     <li>{@value #ORDER}: one order by id;</li>
 *     <li>{@value #ORDERS}: the full list of orders.</li>
 * </ul>
 * Services evict them whenever an order, an item or a user changes (see the {@code @CacheEvict} annotations).
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String ORDER = "order";
    public static final String ORDERS = "orders";

    /** Only key of the {@value #ORDERS} cache. */
    public static final String ALL = "'all'";

    private static final JsonMapper JSON = JsonMapper.builder()
            // Computed getters such as Order.getTotalAmount() are written but have no setter to read them back
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Bean
    public RedisCacheManagerBuilderCustomizer orderCaches(@Value("${bootcamp.cache.time-to-live:10m}") Duration timeToLive) {
        JavaType orderList = JSON.getTypeFactory().constructCollectionType(List.class, Order.class);
        return builder -> builder
                // Cache writes and evictions wait for the transaction to commit: no cached data from a rollback
                .transactionAware()
                .withCacheConfiguration(ORDER, jsonCache(timeToLive, new JacksonJsonRedisSerializer<>(JSON, Order.class)))
                .withCacheConfiguration(ORDERS, jsonCache(timeToLive, new JacksonJsonRedisSerializer<>(JSON, orderList)));
    }

    /**
     * If Valkey is down, log it and read from the database instead of failing the request.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    private static RedisCacheConfiguration jsonCache(Duration timeToLive, JacksonJsonRedisSerializer<?> serializer) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(timeToLive) // Safety net if an eviction is ever missed
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(serializer));
    }
}
