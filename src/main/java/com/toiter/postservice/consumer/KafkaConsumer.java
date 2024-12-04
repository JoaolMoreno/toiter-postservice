package com.toiter.postservice.consumer;

import com.toiter.postservice.model.PostData;
import com.toiter.postservice.model.PostDeletedEvent;
import com.toiter.postservice.model.PostEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class KafkaConsumer {
    private final Logger logger = LoggerFactory.getLogger(KafkaConsumer.class);
    private final RedisTemplate<String, PostData> redisTemplateForPostData;
    private final String POST_ID_DATA_KEY_PREFIX = "post:id:";
    private final String POST_PARENTID_DATA_KEY_PREFIX = "post:parentid:";
    private final String POST_REPOSTID_DATA_KEY_PREFIX = "post:repostid:";

    public KafkaConsumer(RedisTemplate<String, PostData> redisTemplateForPostData) {
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
        redisTemplateForPostData.opsForValue().set(postPublicDataKey, postData, Duration.ofHours(1));

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
        }
    }

    @KafkaListener(topics = {"post-created-topic"}, groupId = "post-event-consumers")
    private void incrementReplyRepostCount(PostEvent event) {
        if(event.getPost().getParentPostId() != null) {
            logger.debug("Incrementing reply count for parent post: {}", event.getPost().getParentPostId());
            PostData parentPostData = redisTemplateForPostData.opsForValue().get(POST_ID_DATA_KEY_PREFIX + event.getPost().getParentPostId());
            if (parentPostData != null) {
                parentPostData.setRepliesCount(parentPostData.getRepliesCount() + 1);
                redisTemplateForPostData.opsForValue().set(POST_ID_DATA_KEY_PREFIX + parentPostData.getId(), parentPostData, Duration.ofHours(1));
            }
        }
        if(event.getPost().getRepostParentId() != null) {
            logger.debug("Incrementing repost count for parent post: {}", event.getPost().getRepostParentId());
            PostData repostParentData = redisTemplateForPostData.opsForValue().get(POST_ID_DATA_KEY_PREFIX + event.getPost().getRepostParentId());
            if (repostParentData != null) {
                repostParentData.setRepostsCount(repostParentData.getRepostsCount() + 1);
                redisTemplateForPostData.opsForValue().set(POST_ID_DATA_KEY_PREFIX + repostParentData.getId(), repostParentData, Duration.ofHours(1));
            }
        }
    }
}