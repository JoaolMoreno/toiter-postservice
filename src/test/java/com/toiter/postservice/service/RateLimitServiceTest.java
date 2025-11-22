package com.toiter.postservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RateLimitServiceTest {

    private RedisTemplate<String, String> redisTemplate;
    private ZSetOperations<String, String> zSetOps;
    private RateLimitService service;

    @BeforeEach
    void setup() {
        redisTemplate = Mockito.mock(RedisTemplate.class);
        zSetOps = Mockito.mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);

        // Default: allow rate limiting
        service = new RateLimitService(redisTemplate, 2, 60, 1, 60, true);
    }

    @Test
    void allowedWhenUnderLimit() {
        when(zSetOps.zCard(anyString())).thenReturn(1L);
        boolean allowed = service.isAllowed(1L, null, RateLimitService.RequestType.GET);
        assertTrue(allowed);
    }

    @Test
    void blockedWhenOverLimit() {
        when(zSetOps.zCard(anyString())).thenReturn(3L);
        boolean allowed = service.isAllowed(null, "1.2.3.4", RateLimitService.RequestType.GET);
        assertFalse(allowed);
    }

    @Test
    void remainingRequests() {
        when(zSetOps.zCard(anyString())).thenReturn(1L);
        long remaining = service.getRemainingRequests(1L, null, RateLimitService.RequestType.OTHER);
        assertEquals(0, remaining); // other limit configured as 1 in setup
    }
}

