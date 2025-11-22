package com.toiter.postservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Service
public class RateLimitService {
    private static final Logger logger = LoggerFactory.getLogger(RateLimitService.class);

    public enum RequestType {
        GET, OTHER
    }

    private final RedisTemplate<String, String> redisTemplate;
    private final int getRequests;
    private final int getWindowSeconds;
    private final int otherRequests;
    private final int otherWindowSeconds;
    private final boolean enabled;

    public RateLimitService(@Qualifier("redisTemplateForString") RedisTemplate<String, String> redisTemplate,
                            @Value("${rate-limit.get.requests:100}") int getRequests,
                            @Value("${rate-limit.get.window-seconds:60}") int getWindowSeconds,
                            @Value("${rate-limit.other.requests:30}") int otherRequests,
                            @Value("${rate-limit.other.window-seconds:60}") int otherWindowSeconds,
                            @Value("${rate-limit.enabled:true}") boolean enabled) {
        this.redisTemplate = redisTemplate;
        this.getRequests = getRequests;
        this.getWindowSeconds = getWindowSeconds;
        this.otherRequests = otherRequests;
        this.otherWindowSeconds = otherWindowSeconds;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAllowed(Long userId, String ipAddress, RequestType type) {
        if (!enabled) return true;

        String key = buildKey(userId, ipAddress, type);
        long now = System.currentTimeMillis();
        double score = (double) now;
        String member = UUID.randomUUID() + "-" + now;

        int windowSeconds = getWindowSecondsForType(type);
        int limit = getLimitForType(type);

        try {
            // Add current request
            redisTemplate.opsForZSet().add(key, member, score);
            // Remove old entries
            double minScore = 0;
            double maxAllowed = (double) (now - (windowSeconds * 1000L));
            redisTemplate.opsForZSet().removeRangeByScore(key, minScore, maxAllowed);
            // Ensure key expires a bit after window
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds + 2));

            Long count = redisTemplate.opsForZSet().zCard(key);
            if (count == null) count = 0L;

            logger.debug("Rate limit check for key={} count={} limit={}", key, count, limit);

            return count <= limit;
        } catch (Exception e) {
            // On Redis errors, fail open (allow) but log
            logger.warn("Redis error while checking rate limit, allowing request: {}", e.getMessage());
            return true;
        }
    }

    public long getRemainingRequests(Long userId, String ipAddress, RequestType type) {
        if (!enabled) return Long.MAX_VALUE;
        String key = buildKey(userId, ipAddress, type);
        int limit = getLimitForType(type);
        try {
            Long count = redisTemplate.opsForZSet().zCard(key);
            if (count == null) count = 0L;
            long remaining = limit - count;
            return Math.max(0, remaining);
        } catch (Exception e) {
            logger.warn("Redis error while getting remaining requests: {}", e.getMessage());
            return Long.MAX_VALUE;
        }
    }

    /**
     * Returns seconds until reset (approximate) for the given key.
     */
    public long getResetTime(Long userId, String ipAddress, RequestType type) {
        if (!enabled) return 0L;
        String key = buildKey(userId, ipAddress, type);
        int windowSeconds = getWindowSecondsForType(type);
        try {
            // Get the oldest entry (lowest score) and compute difference to window
            Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().rangeWithScores(key, 0, 0);
            if (tuples == null || tuples.isEmpty()) {
                return windowSeconds;
            }
            ZSetOperations.TypedTuple<String> oldest = tuples.iterator().next();
            Double score = oldest.getScore();
            if (score == null) return windowSeconds;
            long oldestMillis = score.longValue();
            long now = System.currentTimeMillis();
            long resetMillis = (oldestMillis + (windowSeconds * 1000L)) - now;
            return resetMillis <= 0 ? 0 : (resetMillis / 1000);
        } catch (Exception e) {
            logger.warn("Redis error while getting reset time: {}", e.getMessage());
            return windowSeconds;
        }
    }

    public int getLimitForType(RequestType type) {
        return type == RequestType.GET ? getRequests : otherRequests;
    }

    private int getWindowSecondsForType(RequestType type) {
        return type == RequestType.GET ? getWindowSeconds : otherWindowSeconds;
    }

    private String buildKey(Long userId, String ipAddress, RequestType type) {
        String typeName = type == RequestType.GET ? "get" : "other";
        if (userId != null) {
            return String.format("rate_limit:user:%d:%s", userId, typeName);
        }
        return String.format("rate_limit:ip:%s:%s", ipAddress != null ? ipAddress : "unknown", typeName);
    }
}
