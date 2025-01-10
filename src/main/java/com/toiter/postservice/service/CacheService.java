package com.toiter.postservice.service;

import com.toiter.postservice.model.PostData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class CacheService {
    private final Logger logger = LoggerFactory.getLogger(PostService.class);
    private final String POST_ID_DATA_KEY_PREFIX = "post:id:";
    private final String POST_PARENTID_DATA_KEY_PREFIX = "post:parentid:";
    private final String POST_REPOSTID_DATA_KEY_PREFIX = "post:repostid:";
    private final RedisTemplate<String, PostData> redisTemplateForPostData;
    private final RedisTemplate<String, Long> redisTemplateForSet;

    public CacheService(RedisTemplate<String, PostData> redisTemplateForPostData, RedisTemplate<String, Long> redisTemplateForSet) {
        this.redisTemplateForPostData = redisTemplateForPostData;
        this.redisTemplateForSet = redisTemplateForSet;
    }

    public void cachePostData(PostData postData) {
        Long userId = postData.getUserId();
        String userIndexKey = "user:posts:" + userId;

        logger.debug("Caching post data for ID: {}", postData.getId());

        redisTemplateForPostData.opsForValue().set(POST_ID_DATA_KEY_PREFIX + postData.getId(), postData, Duration.ofHours(1));
        redisTemplateForSet.opsForSet().add(userIndexKey, postData.getId());
        redisTemplateForSet.expire(userIndexKey, Duration.ofHours(1));

        logger.debug("Post data cached successfully for ID: {} and added to user index for userId: {}", postData.getId(), userId);
    }

    public PostData getCachedPostById(Long postId) {
        logger.debug("Fetching post data for ID on Cache: {}", postId);
        PostData post = redisTemplateForPostData.opsForValue().get(POST_ID_DATA_KEY_PREFIX + postId);
        if(post != null) {
            redisTemplateForPostData.expire(POST_ID_DATA_KEY_PREFIX + postId, Duration.ofHours(1));
            return post;
        }
        return null;
    }

    public void deletePostData(PostData postData) {
        Long userId = postData.getUserId();
        logger.debug("Deleting post data for ID: {}", userId);
        String userIndexKey = String.format("user:posts:%s", userId);

        redisTemplateForPostData.opsForValue().set(POST_ID_DATA_KEY_PREFIX + postData.getId(), postData, Duration.ofHours(1));
        redisTemplateForSet.opsForSet().remove(userIndexKey);
    }
}
