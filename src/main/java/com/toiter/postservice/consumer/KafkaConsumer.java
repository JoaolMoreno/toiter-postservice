package com.toiter.postservice.consumer;

import com.toiter.postservice.entity.Post;
import com.toiter.postservice.model.*;
import com.toiter.postservice.service.CacheService;
import com.toiter.postservice.repository.PostRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KafkaConsumer {
    private final Logger logger = LoggerFactory.getLogger(KafkaConsumer.class);
    private final CacheService cacheService;
    private final PostRepository postRepository;

    public KafkaConsumer(CacheService cacheService, PostRepository postRepository) {
        this.cacheService = cacheService;
        this.postRepository = postRepository;
    }

    @KafkaListener(topics = {"post-created-topic", "post-deleted-topic"}, groupId = "post-event-consumers")
    private void processPostEvent(PostEvent event) {
        logger.debug("Received event: {}", event);
        PostData postData = new PostData(event.getPost());

        switch (event) {
            case PostCreatedEvent postCreatedEvent -> {
                if(!cacheService.existsPostById(postCreatedEvent.getPost().getId())){
                    postData.setLikesCount(0);
                    postData.setRepostsCount(0);
                    postData.setViewCount(0);
                    cacheService.cachePostData(postData);

                }
                incrementReplyRepostCount(postCreatedEvent);
            }
            case PostDeletedEvent postDeletedEvent -> {
                cacheService.deletePostData(postData);
                decrementReplyReposCount(postDeletedEvent);
                List<Post> reposts = postRepository.findRepostsByRepostParentId(postDeletedEvent.getPost().getId());
                for (Post repost : reposts) {
                    if (repost.getContent() == null || repost.getContent().isEmpty()) {
                        repost.setContent("");
                        repost.setMediaUrl(null);
                        repost.setDeletedAt(java.time.LocalDateTime.now());
                        repost.setDeleted(true);
                        postRepository.save(repost);
                        PostData repostData = cacheService.getCachedPostById(repost.getId());
                        if (repostData != null) {
                            repostData.setContent("");
                            repostData.setMediaUrl(null);
                            repostData.setDeleted(true);
                            cacheService.cachePostData(repostData);
                        }
                    } else {
                        PostData repostData = cacheService.getCachedPostById(repost.getId());
                        if (repostData != null) {
                            repostData.setRepostPostData(null);
                            cacheService.cachePostData(repostData);
                        }
                    }
                }
            }
            default -> throw new IllegalStateException("Unexpected value: " + event);
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

    private void decrementReplyReposCount(PostEvent event){
        logger.debug("Decrementing reply and repost count for post: {}", event.getPost().toString());
        if(event.getPost().getParentPostId() != null) {
            logger.debug("Decrementing reply count for parent post: {}", event.getPost().toString());
            PostData parentPostData = cacheService.getCachedPostById(event.getPost().getParentPostId());
            if (parentPostData != null) {
                parentPostData.setRepliesCount(parentPostData.getRepliesCount() - 1);
                cacheService.cachePostData(parentPostData);
            }
        }
        if(event.getPost().getRepostParentId() != null) {
            logger.debug("Decrementing repost count for parent post: {}", event.getPost().toString());
            PostData repostParentData = cacheService.getCachedPostById(event.getPost().getRepostParentId());
            if (repostParentData != null) {
                repostParentData.setRepostsCount(repostParentData.getRepostsCount() - 1);
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
        cacheService.setLikeStatus(event.getUserId(), event.getPostId(), increment == 1);
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