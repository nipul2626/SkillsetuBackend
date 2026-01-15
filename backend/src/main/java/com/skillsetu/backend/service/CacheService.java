package com.skillsetu.backend.service;

import jakarta.persistence.Entity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
@Slf4j
@RequiredArgsConstructor
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Cache-Aside Pattern:
     * 1. Try Redis
     * 2. Compute if missing
     * 3. Cache ONLY if safe
     */
    public <T> T getOrCompute(
            String key,
            Class<T> type,
            Supplier<T> computeFunction,
            Duration ttl
    ) {
        try {
            // 1️⃣ Try to get from cache
            Object cached = redisTemplate.opsForValue().get(key);

            if (cached != null) {
                log.debug("✅ Cache HIT for key: {}", key);
                return type.cast(cached);
            }

            // 2️⃣ Cache MISS → compute
            log.debug("⚠️ Cache MISS for key: {}", key);
            T computed = computeFunction.get();

            if (computed == null) {
                return null;
            }

            // 3️⃣ ❌ NEVER cache JPA entities
            if (isJpaEntity(computed)) {
                log.warn(
                        "🚫 Skipping Redis cache for JPA entity: {} (key={})",
                        computed.getClass().getSimpleName(),
                        key
                );
                return computed;
            }

            // 4️⃣ Safe to cache
            redisTemplate.opsForValue().set(key, computed, ttl);
            log.debug("📦 Cached value for key: {}", key);

            return computed;

        } catch (Exception e) {
            log.error(
                    "❌ Redis error for key: {}. Falling back to computation.",
                    key,
                    e
            );
            return computeFunction.get();
        }
    }

    /**
     * Check if object is a JPA Entity
     */
    private boolean isJpaEntity(Object obj) {
        return obj.getClass().isAnnotationPresent(Entity.class);
    }

    // ===================== OTHER METHODS =====================

    public void put(String key, Object value, Duration ttl) {

        if (value != null && isJpaEntity(value)) {
            log.warn(
                    "🚫 Skipping Redis put for JPA entity: {} (key={})",
                    value.getClass().getSimpleName(),
                    key
            );
            return;
        }

        redisTemplate.opsForValue().set(key, value, ttl);
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void invalidate(String key) {
        redisTemplate.delete(key);
        log.debug("🗑️ Invalidated cache for key: {}", key);
    }

    public void invalidatePattern(String pattern) {
        var keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.debug("🗑️ Invalidated {} keys matching pattern: {}", keys.size(), pattern);
        }
    }

    public Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    public boolean expire(String key, Duration ttl) {
        return Boolean.TRUE.equals(
                redisTemplate.expire(key, ttl.toMillis(), TimeUnit.MILLISECONDS)
        );
    }
}
