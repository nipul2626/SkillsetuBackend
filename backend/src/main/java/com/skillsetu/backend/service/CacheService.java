package com.skillsetu.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Cache-Aside Pattern: Get from cache, or compute and store
     */
    public <T> T getOrCompute(String key, Class<T> type,
                              java.util.function.Supplier<T> computeFunction,
                              Duration ttl) {
        try {
            // 1. Try to get from cache
            Object cached = redisTemplate.opsForValue().get(key);

            if (cached != null) {
                log.debug("Cache HIT for key: {}", key);
                return type.cast(cached);
            }

            // 2. Cache MISS - compute value
            log.debug("Cache MISS for key: {}", key);
            T computed = computeFunction.get();

            // 3. Store in cache
            if (computed != null) {
                redisTemplate.opsForValue().set(key, computed, ttl);
                log.debug("Cached value for key: {}", key);
            }

            return computed;

        } catch (Exception e) {
            log.error("Cache error for key: {}. Falling back to computation.", key, e);
            return computeFunction.get();
        }
    }

    /**
     * Invalidate cache entry
     */
    public void invalidate(String key) {
        redisTemplate.delete(key);
        log.debug("Invalidated cache for key: {}", key);
    }

    /**
     * Invalidate cache pattern (e.g., "student:123:*")
     */
    public void invalidatePattern(String pattern) {
        var keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.debug("Invalidated {} keys matching pattern: {}", keys.size(), pattern);
        }
    }

    /**
     * Store value with TTL
     */
    public void put(String key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    /**
     * Get value from cache
     */
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * Check if key exists
     */
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Increment counter (useful for analytics)
     */
    public Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    /**
     * Set expiration on existing key
     */
    public boolean expire(String key, Duration ttl) {
        return Boolean.TRUE.equals(redisTemplate.expire(key, ttl.toMillis(), TimeUnit.MILLISECONDS));
    }
}
