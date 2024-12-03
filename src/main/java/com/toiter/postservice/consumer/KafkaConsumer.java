package com.toiter.postservice.consumer;

import com.toiter.postservice.model.PostCreatedEvent;
import com.toiter.postservice.model.PostData;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class KafkaConsumer {
    private final RedisTemplate<String, PostData> redisTemplateForPostData;
    private final String POST_ID_DATA_KEY_PREFIX = "post:id:";
    private final String POST_PARENTID_DATA_KEY_PREFIX = "post:parentid:";
    private final String POST_REPOSTID_DATA_KEY_PREFIX = "post:repostid:";

    public KafkaConsumer(RedisTemplate<String, PostData> redisTemplateForPostData) {
        this.redisTemplateForPostData = redisTemplateForPostData;
    }

    @KafkaListener(topics = "post-created-topic", groupId = "post-event-consumers")
    private void processPostEvent(PostCreatedEvent event) {
        PostData postData = new PostData(event);

        // Salvar por ID do Post
        String postPublicDataKey = POST_ID_DATA_KEY_PREFIX + postData.getId();
        redisTemplateForPostData.opsForValue().set(postPublicDataKey, postData, Duration.ofHours(1));

        // Salvar por Parent ID
        if (postData.getParentPostId() != null) {
            String postParentDataKey = POST_PARENTID_DATA_KEY_PREFIX + postData.getParentPostId();
            redisTemplateForPostData.opsForList().rightPush(postParentDataKey, postData);
            redisTemplateForPostData.expire(postParentDataKey, Duration.ofHours(1));
        }

        // Salvar por Repost ID
        if (postData.getRepostParentId() != null) {
            String postRepostDataKey = POST_REPOSTID_DATA_KEY_PREFIX + postData.getRepostParentId();
            redisTemplateForPostData.opsForList().rightPush(postRepostDataKey, postData);
            redisTemplateForPostData.expire(postRepostDataKey, Duration.ofHours(1));
        }
    }
}
