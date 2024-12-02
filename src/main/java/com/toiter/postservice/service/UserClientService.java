package com.toiter.postservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Service
public class UserClientService {

    private final RestTemplate restTemplate;

    @Value("${service.user.url}")
    private String userServiceUrl;

    @Value("${service.shared-key}")
    private String sharedKey;

    private final RedisTemplate<String, Long> redisTemplateForLong;

    private static final String USERNAME_TO_ID_KEY_PREFIX = "user:username:";

    private final Logger logger = LoggerFactory.getLogger(UserClientService.class);

    public UserClientService(RestTemplate restTemplate, RedisTemplate<String, Long> redisTemplateForLong) {
        this.restTemplate = restTemplate;
        this.redisTemplateForLong = redisTemplateForLong;
    }

    public Long getUserIdByUsername(String username) {
        String userIdKey = USERNAME_TO_ID_KEY_PREFIX + username;
        ValueOperations<String, Long> valueOpsForLong = redisTemplateForLong.opsForValue();
        Number rawValue = valueOpsForLong.get(userIdKey);
        Long userId = rawValue != null ? rawValue.longValue() : null;

        if (userId == null) {
            logger.debug("User ID not found in cache, fetching from user service");
            String url = userServiceUrl + "/users/" + username + "/id";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + sharedKey);

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Long> response = restTemplate.exchange(url, HttpMethod.GET, entity, Long.class);
            userId = response.getBody();

            if(userId == null) {
                throw new RuntimeException("User not found");
            }

            valueOpsForLong.set(userIdKey, userId);
        }
        else {
            logger.debug("User ID found in cache");
        }
        valueOpsForLong.getOperations().expire(userIdKey, Duration.ofHours(1));

        return userId;
    }
}

