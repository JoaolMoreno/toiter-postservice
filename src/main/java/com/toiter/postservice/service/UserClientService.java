package com.toiter.postservice.service;

import com.toiter.postservice.model.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class UserClientService {

    private final RestTemplate restTemplate;

    @Value("${service.user.url}")
    private String userServiceUrl;

    @Value("${service.shared-key}")
    private String sharedKey;

    private final Logger logger = LoggerFactory.getLogger(UserClientService.class);

    public UserClientService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Long getUserIdByUsername(String username) {
        logger.debug("Fetching user ID for username: {}", username);
        String url = userServiceUrl + "/users/" + username + "/id";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + sharedKey);

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<Long> response = restTemplate.exchange(url, HttpMethod.GET, entity, Long.class);
        Long userId = response.getBody();

        if(userId == null) {
            throw new RuntimeException("User not found");
        }

        return userId;
    }

    public UserResponse getUserById(Long userId) {
        logger.debug("Fetching user for user ID: {}", userId);
        String url = userServiceUrl + "/users/" + userId + "/user";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + sharedKey);

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<UserResponse> response = restTemplate.exchange(url, HttpMethod.GET, entity, UserResponse.class);
        UserResponse userResponse = response.getBody();

        if (userResponse == null) {
            throw new RuntimeException("User not found");
        }

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
