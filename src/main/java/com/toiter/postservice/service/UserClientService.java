package com.toiter.postservice.service;

import com.toiter.userservice.model.UserResponse;
import com.toiter.userservice.model.UserPublicData;
import com.toiter.userservice.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private final CacheService cacheService;

    @Value("${service.user.url}")
    private String userServiceUrl;

    @Value("${service.shared-key}")
    private String sharedKey;

    @Value("${server.url}")
    private String serverUrl;

    private final Logger logger = LoggerFactory.getLogger(UserClientService.class);

    public UserClientService(RestTemplate restTemplate, CacheService cacheService) {
        this.restTemplate = restTemplate;
        this.cacheService = cacheService;
    }

    public Long getUserIdByUsername(String username) {
        logger.debug("Fetching user ID for username: {}", username);

        Long cachedUserId = cacheService.getCachedUserIdByUsername(username);
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

        User cachedUser = cacheService.getCachedUserById(userId);
        if (cachedUser != null) {
            logger.debug("CACHE HIT: user object found in cache for user ID: {}", userId);
            return new UserResponse(cachedUser);
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

    public UserPublicData getUserPublicData(Long userId) {
        logger.debug("Fetching public data for user ID: {}", userId);

        UserPublicData cachedData = cacheService.getCachedUserPublicData(userId);
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
}
