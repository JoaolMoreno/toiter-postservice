package com.toiter.postservice.service;

import com.toiter.postservice.model.UserResponse;
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
    private final RedisTemplate<String, String> redisTemplateForString;

    private static final String USERNAME_TO_ID_KEY_PREFIX = "user:username:";

    private final Logger logger = LoggerFactory.getLogger(UserClientService.class);

    public UserClientService(RestTemplate restTemplate, RedisTemplate<String, Long> redisTemplateForLong, RedisTemplate<String, String> redisTemplateForString) {
        this.restTemplate = restTemplate;
        this.redisTemplateForLong = redisTemplateForLong;
        this.redisTemplateForString = redisTemplateForString;
    }

    public Long getUserIdByUsername(String username) {
        logger.debug("Fetching user ID for username: {}", username);
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

    public UserResponse getUserById(Long userId) {
        logger.debug("Fetching user for user ID: {}", userId);
        String usernameKey = USERNAME_TO_ID_KEY_PREFIX + "id:" + userId;
        String displayNameKey = USERNAME_TO_ID_KEY_PREFIX + "display:" + userId;
        ValueOperations<String, String> valueOpsForString = redisTemplateForString.opsForValue();
        String username = valueOpsForString.get(usernameKey);
        String displayName = valueOpsForString.get(displayNameKey);

        if (username == null || displayName == null) {
            logger.debug("Username or display name not found in cache, fetching from user service");
            String url = userServiceUrl + "/users/" + userId + "/user";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + sharedKey);

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<UserResponse> response = restTemplate.exchange(url, HttpMethod.GET, entity, UserResponse.class);
            UserResponse userResponse = response.getBody();

            if (userResponse == null) {
                throw new RuntimeException("User not found");
            }

            username = userResponse.getUsername();
            displayName = userResponse.getDisplayName();

            valueOpsForString.set(usernameKey, username);
            valueOpsForString.set(displayNameKey, displayName);
        } else {
            logger.debug("Username and display name found in cache");
        }
        valueOpsForString.getOperations().expire(usernameKey, Duration.ofHours(1));
        valueOpsForString.getOperations().expire(displayNameKey, Duration.ofHours(1));

        UserResponse userResponse = new UserResponse();
        userResponse.setUsername(username);
        userResponse.setDisplayName(displayName);
        return userResponse;
    }

    public String getUserProfilePicture(String username) {
        logger.debug("Fetching user profile picture for username: {}", username);
        String url = userServiceUrl + "/users/" + username + "/profile-picture";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + sharedKey);

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        return response.getBody();
    }

    public String getDisplayNameById(Long userId) {
        logger.debug("Fetching display name for user ID: {}", userId);
        UserResponse userResponse = getUserById(userId);
        return userResponse.getDisplayName();
    }
}
