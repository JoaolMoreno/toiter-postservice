package com.toiter.postservice.consumer;

import com.toiter.postservice.model.*;
import com.toiter.postservice.service.LikeService;
import com.toiter.postservice.service.PostService;
import com.toiter.postservice.service.UserClientService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.Optional;

@Service
public class KafkaConsumer {
    private final Logger logger = LoggerFactory.getLogger(KafkaConsumer.class);
    private final PostService postService;
    private final LikeService likeService;
    private final UserClientService userClientService;
    private final RedisTemplate<String, PostData> redisTemplateForPostData;
    private final String POST_ID_DATA_KEY_PREFIX = "post:id:";
    private final String POST_PARENTID_DATA_KEY_PREFIX = "post:parentid:";
    private final String POST_REPOSTID_DATA_KEY_PREFIX = "post:repostid:";

    public KafkaConsumer(PostService postService, LikeService likeService, UserClientService userClientService, RedisTemplate<String, PostData> redisTemplateForPostData) {
        this.postService = postService;
        this.likeService = likeService;
        this.userClientService = userClientService;
        this.redisTemplateForPostData = redisTemplateForPostData;
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

        // Save by Post ID
        String postPublicDataKey = POST_ID_DATA_KEY_PREFIX + postData.getId();

        String username = userClientService.getUsernameById(postData.getUserId());
        postData.setUsername(username);

        // Save by Parent ID
        if (postData.getParentPostId() != null) {
            String postParentDataKey = POST_PARENTID_DATA_KEY_PREFIX + postData.getParentPostId();
            redisTemplateForPostData.opsForList().rightPush(postParentDataKey, postData);
            redisTemplateForPostData.expire(postParentDataKey, Duration.ofHours(1));
        }

        // Save by Repost ID
        if (postData.getRepostParentId() != null) {
            String postRepostDataKey = POST_REPOSTID_DATA_KEY_PREFIX + postData.getRepostParentId();
            redisTemplateForPostData.opsForList().rightPush(postRepostDataKey, postData);
            redisTemplateForPostData.expire(postRepostDataKey, Duration.ofHours(1));

            // Save reposted post data
            Optional<PostData> repostedPostData = postService.getPostById(postData.getRepostParentId(),0, null);
            repostedPostData.ifPresent(postData::setRepostPostData);
        }
        redisTemplateForPostData.opsForValue().set(postPublicDataKey, postData, Duration.ofHours(1));

        if(event instanceof PostCreatedEvent) {
            incrementReplyRepostCount(event);
        }
    }

    private void incrementReplyRepostCount(PostEvent event) {
        logger.debug("Incrementing reply and repost count for post: {}", event.getPost().toString());
        if(event.getPost().getParentPostId() != null) {
            PostData parentPostData = redisTemplateForPostData.opsForValue().get(POST_ID_DATA_KEY_PREFIX + event.getPost().getParentPostId());
            if (parentPostData != null) {
                parentPostData.setRepliesCount(parentPostData.getRepliesCount() + 1);
                redisTemplateForPostData.opsForValue().set(POST_ID_DATA_KEY_PREFIX + parentPostData.getId(), parentPostData, Duration.ofHours(1));
            }
        }
        if(event.getPost().getRepostParentId() != null) {
            logger.debug("Incrementing repost count for parent post: {}", event.getPost().toString());
            PostData repostParentData = redisTemplateForPostData.opsForValue().get(POST_ID_DATA_KEY_PREFIX + event.getPost().getRepostParentId());
            if (repostParentData != null) {
                repostParentData.setRepostsCount(repostParentData.getRepostsCount() + 1);
                redisTemplateForPostData.opsForValue().set(POST_ID_DATA_KEY_PREFIX + repostParentData.getId(), repostParentData, Duration.ofHours(1));
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
        PostData postData = redisTemplateForPostData.opsForValue().get(POST_ID_DATA_KEY_PREFIX + event.getPostId());
        if (postData != null) {
            postData.setLikesCount(postData.getLikesCount() + increment);
            redisTemplateForPostData.opsForValue().set(POST_ID_DATA_KEY_PREFIX + postData.getId(), postData, Duration.ofHours(1));
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
        PostData postData = redisTemplateForPostData.opsForValue().get(POST_ID_DATA_KEY_PREFIX + event.getPostId());
        if (postData != null) {
            postData.setViewCount(postData.getViewCount() + 1);
            redisTemplateForPostData.opsForValue().set(POST_ID_DATA_KEY_PREFIX + postData.getId(), postData, Duration.ofHours(1));
        }
    }
}