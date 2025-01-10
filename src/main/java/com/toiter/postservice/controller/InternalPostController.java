package com.toiter.postservice.controller;

import com.toiter.postservice.service.PostService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/posts")
public class InternalPostController {

    private static final Logger logger = LoggerFactory.getLogger(InternalPostController.class);

    private final PostService postService;

    public InternalPostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping("/count")
    public Integer getPostsCount(Long userId) {
        logger.debug("Fetching posts count for user ID: {}", userId);
        return postService.getPostsCount(userId);
    }

    @PostMapping("/update-profile-image")
    public void updateProfileImage(Long userId, String imageUrl) {
        logger.debug("Updating profile image for user ID: {}", userId);
        postService.updateProfileImage(userId, imageUrl);
    }
}
