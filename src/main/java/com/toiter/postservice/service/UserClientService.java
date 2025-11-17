package com.toiter.postservice.service;

import com.toiter.postservice.model.UserResponse;
import com.toiter.userservice.model.UserPublicData;
import com.toiter.userservice.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Serviço cliente para o Serviço de Usuário, responsável por operações relacionadas a usuários.
 * Todo o cache é gerenciado pelo Serviço de Usuário; este serviço apenas lê do cache.
 */
@Service
public class UserClientService {

    private final RestTemplate restTemplate;
    private final RedisTemplate<String, Long> redisTemplateForLong;
    private final RedisTemplate<String, UserPublicData> redisTemplateForUserPublicData;
    private final RedisTemplate<String, User> redisTemplateForUser;

    @Value("${service.user.url}")
    private String userServiceUrl;

    @Value("${service.shared-key}")
    private String sharedKey;

    @Value("${server.url}")
    private String serverUrl;

    private final Logger logger = LoggerFactory.getLogger(UserClientService.class);

    public UserClientService(RestTemplate restTemplate, RedisTemplate<String, Long> redisTemplateForLong, RedisTemplate<String, UserPublicData> redisTemplateForUserPublicData, RedisTemplate<String, User> redisTemplateForUser) {
        this.restTemplate = restTemplate;
        this.redisTemplateForLong = redisTemplateForLong;
        this.redisTemplateForUserPublicData = redisTemplateForUserPublicData;
        this.redisTemplateForUser = redisTemplateForUser;
    }

    public Long getUserIdByUsername(String username) {
        logger.debug("Fetching user ID for username: {}", username);

        String cacheKey = "user:username:" + username;
        Number rawValue = redisTemplateForLong.opsForValue().get(cacheKey);
        Long cachedUserId = rawValue != null ? rawValue.longValue() : null;
        if (cachedUserId != null) {
            logger.debug("CACHE HIT: user id for username '{}' -> {}", username, cachedUserId);
            return cachedUserId;
        }

        logger.debug("CACHE MISS: user id for username '{}'. Falling back to user service.", username);
        String url = userServiceUrl + "/users/" + username + "/id";
        logger.debug("Calling user service URL: {}", url);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + sharedKey);

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<Number> response = restTemplate.exchange(url, HttpMethod.GET, entity, Number.class);
        Number body = response.getBody();
        if (body == null) {
            logger.debug("User service returned null for username: {}", username);
            throw new RuntimeException("User not found");
        }

        logger.debug("User service returned id {} for username {}", body.longValue(), username);
        return body.longValue();
    }

    public UserResponse getUserById(Long userId) {
        logger.debug("Fetching user for user ID: {}", userId);

        String cacheKey = "user:id:" + userId;
        User cachedUser = redisTemplateForUser.opsForValue().get(cacheKey);
        if (cachedUser != null) {
            logger.debug("CACHE HIT: user object found in cache for user ID: {}", userId);
            UserResponse userResponse = new UserResponse();
            userResponse.setId(cachedUser.getId());
            userResponse.setUsername(cachedUser.getUsername());
            userResponse.setDisplayName(cachedUser.getDisplayName());
            userResponse.setEmail(cachedUser.getEmail());
            userResponse.setBio(cachedUser.getBio());
            userResponse.setProfileImageId(cachedUser.getProfileImageId());
            userResponse.setHeaderImageId(cachedUser.getHeaderImageId());
            userResponse.setCreationDate(cachedUser.getCreationDate());
            return userResponse;
        }

        logger.debug("CACHE MISS: user object not found in cache for user ID: {}. Falling back to user service.", userId);
        String url = userServiceUrl + "/users/" + userId + "/user";
        logger.debug("Calling user service URL: {}", url);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + sharedKey);

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<UserResponse> response = restTemplate.exchange(url, HttpMethod.GET, entity, UserResponse.class);
        UserResponse userResponse = response.getBody();

        if (userResponse == null) {
            logger.debug("User service returned null for user ID: {}", userId);
            throw new RuntimeException("User not found");
        }

        logger.debug("User service returned user with id {} for user ID: {}", userResponse.getId(), userId);
        return userResponse;
    }

    public String getUserProfilePicture(String username) {
        logger.debug("Fetching user profile picture for username: {}", username);

        Long userId = getUserIdByUsername(username);
        UserPublicData data = getUserPublicData(userId);
        if (data != null && data.getProfileImageId() != null) {
            logger.debug("CACHE HIT: user public data/profile image found for username: {} (userId={}) -> profileImageId={}", username, userId, data.getProfileImageId());
            return getProfilePictureUrl(data.getProfileImageId());
        }

        logger.debug("CACHE MISS: profile image not available in public data for username: {} (userId={}). Falling back to user service.", username, userId);
        String url = userServiceUrl + "/users/" + username + "/profile-picture";
        logger.debug("Calling user service URL: {}", url);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + sharedKey);

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        String body = response.getBody();
        logger.debug("User service returned profile picture value for username {}: {}", username, body != null ? "<non-null>" : "<null>");
        return body;
    }

    public UserPublicData getUserPublicData(Long userId) {
        logger.debug("Fetching public data for user ID: {}", userId);

        String cacheKey = "user:public:" + userId;
        UserPublicData cachedData = redisTemplateForUserPublicData.opsForValue().get(cacheKey);
        if (cachedData != null) {
            logger.debug("CACHE HIT: public data found in cache for user ID: {}", userId);
            return cachedData;
        }

        logger.debug("CACHE MISS: public data not found in cache for user ID: {}. Falling back to user service.", userId);
        String url = userServiceUrl + "/users/public?userId=" + userId;
        logger.debug("Calling user service URL: {}", url);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + sharedKey);

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<UserPublicData> response = restTemplate.exchange(url, HttpMethod.GET, entity, UserPublicData.class);
        UserPublicData userPublicData = response.getBody();

        if (userPublicData == null) {
            logger.debug("User service returned null public data for user ID: {}", userId);
            throw new RuntimeException("User public data not found");
        }

        logger.debug("User service returned public data for user ID: {}", userId);
        return userPublicData;
    }

    public String getProfilePictureUrl(Long profileImageId) {
        return profileImageId != null ? serverUrl + "/images/" + profileImageId : null;
    }
}
