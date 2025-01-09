package com.toiter.postservice.consumer;

import com.toiter.postservice.model.*;
import com.toiter.postservice.service.CacheService;
import com.toiter.postservice.service.LikeService;
import com.toiter.postservice.service.UserClientService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumer {
    private final Logger logger = LoggerFactory.getLogger(KafkaConsumer.class);
    private final LikeService likeService;
    private final UserClientService userClientService;
    private final CacheService cacheService;

    public KafkaConsumer(LikeService likeService, UserClientService userClientService, CacheService cacheService) {
        this.likeService = likeService;
        this.userClientService = userClientService;
        this.cacheService = cacheService;
    }

    @KafkaListener(topics = {"post-created-topic", "post-deleted-topic"}, groupId = "post-event-consumers")
    private void processPostEvent(PostEvent event) {
        logger.debug("Received event: {}", event);
        PostData postData = new PostData(event.getPost());
        if(event instanceof PostDeletedEvent) {
            postData.setLikesCount(0);
            postData.setRepostsCount(0);
            postData.setViewCount(0);
        }

        String username = userClientService.getUsernameById(postData.getUserId());
        postData.setUsername(username);
        postData.setProfilePicture(userClientService.getUserProfilePicture(username));

        cacheService.cachePostData(postData);

        if(event instanceof PostCreatedEvent) {
            incrementReplyRepostCount(event);
        }
    }

    private void incrementReplyRepostCount(PostEvent event) {
        logger.debug("Incrementing reply and repost count for post: {}", event.getPost().toString());
        if(event.getPost().getParentPostId() != null) {
            PostData parentPostData = cacheService.getCachedPostById(event.getPost().getParentPostId());
            if (parentPostData != null) {
                parentPostData.setRepliesCount(parentPostData.getRepliesCount() + 1);
                cacheService.cachePostData(parentPostData);
            }
        }
        if(event.getPost().getRepostParentId() != null) {
            logger.debug("Incrementing repost count for parent post: {}", event.getPost().toString());
            PostData repostParentData = cacheService.getCachedPostById(event.getPost().getRepostParentId());
            if (repostParentData != null) {
                repostParentData.setRepostsCount(repostParentData.getRepostsCount() + 1);
                cacheService.cachePostData(repostParentData);
            }
        }
    }

    @KafkaListener(topics = "like-events-topic", groupId = "like-event-consumers")
    private void processLikeEvent(LikeEvent event) {
        logger.debug("Received event: {}", event);
        switch (event){
            case PostLikedEvent ignored -> incrementLikeCount(event, 1);
            case PostUnlikedEvent ignored -> incrementLikeCount(event, -1);
            default -> throw new IllegalStateException("Unexpected value: " + event);
        }
    }

    private void incrementLikeCount(LikeEvent event, @Min(-1) @Max(1) int increment) {
        logger.debug("Incrementing like count for post: {}", event.toString());
        PostData postData = cacheService.getCachedPostById(event.getPostId());
        if (postData != null) {
            postData.setLikesCount(postData.getLikesCount() + increment);
            cacheService.cachePostData(postData);
            logger.debug("Like count incremented for post: {}, likes: {}", event.getPostId(), postData.getLikesCount());
        }
        if(increment == 1) {
            likeService.registerLikeInRedis(event.getUserId(), event.getPostId());
        } else {
            likeService.removeLikeFromRedis(event.getUserId(), event.getPostId());
        }
    }

    @KafkaListener(topics = "post-viewed-topic", groupId = "view-event-consumers")
    private void processViewEvent(PostViewedEvent event) {
        logger.debug("Received event: {}", event);
        PostData postData = cacheService.getCachedPostById(event.getPostId());
        if (postData != null) {
            postData.setViewCount(postData.getViewCount() + 1);
            cacheService.cachePostData(postData);
        }
    }
}