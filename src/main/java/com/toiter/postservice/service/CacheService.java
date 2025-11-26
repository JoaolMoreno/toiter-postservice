package com.toiter.postservice.service;

import com.toiter.postservice.model.PostData;
import com.toiter.userservice.entity.User;
import com.toiter.userservice.model.UserPublicData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

@Service
public class CacheService {
    private final Logger logger = LoggerFactory.getLogger(CacheService.class);
    private final String POST_ID_DATA_KEY_PREFIX = "post:id:";
    private final String LIKE_KEY_PREFIX = "like:user:";
    private final RedisTemplate<String, PostData> redisTemplateForPostData;
    private final RedisTemplate<String, Long> redisTemplateForSet;
    private final RedisTemplate<String, Boolean> redisTemplateForLike;
    private final RedisTemplate<String, Long> redisTemplateForLong;
    private final RedisTemplate<String, UserPublicData> redisTemplateForUserPublicData;
    private final RedisTemplate<String, User> redisTemplateForUser;
    private final RedisTemplate<String, String> redisTemplateForString;
    private final RedisLockRegistry redisLockRegistry;

    public CacheService(RedisTemplate<String, PostData> redisTemplateForPostData, RedisTemplate<String, Long> redisTemplateForSet, RedisTemplate<String, Boolean> redisTemplateForLike, RedisTemplate<String, Long> redisTemplateForLong, RedisTemplate<String, UserPublicData> redisTemplateForUserPublicData, RedisTemplate<String, User> redisTemplateForUser, RedisTemplate<String, String> redisTemplateForString, RedisLockRegistry redisLockRegistry) {
        this.redisTemplateForPostData = redisTemplateForPostData;
        this.redisTemplateForSet = redisTemplateForSet;
        this.redisTemplateForLike = redisTemplateForLike;
        this.redisTemplateForLong = redisTemplateForLong;
        this.redisTemplateForUserPublicData = redisTemplateForUserPublicData;
        this.redisTemplateForUser = redisTemplateForUser;
        this.redisTemplateForString = redisTemplateForString;
        this.redisLockRegistry = redisLockRegistry;
    }

    private PostData sanitizeForCache(PostData source) {
        if (source == null) return null;
        PostData sanitized = new PostData();
        // Core identifiers and structure
        sanitized.setId(source.getId());
        sanitized.setParentPostId(source.getParentPostId());
        sanitized.setRepostParentId(source.getRepostParentId());
        sanitized.setRepost(source.getRepost());
        sanitized.setReply(source.getReply());
        sanitized.setUserId(source.getUserId());

        // Post content/media
        sanitized.setContent(source.getContent());
        sanitized.setMediaUrl(source.getMediaUrl());

        // Counters/state
        sanitized.setLikesCount(source.getLikesCount());
        sanitized.setRepliesCount(source.getRepliesCount());
        sanitized.setRepostsCount(source.getRepostsCount());
        sanitized.setViewCount(source.getViewCount());
        sanitized.setDeleted(source.isDeleted());
        sanitized.setCreatedAt(source.getCreatedAt());

        // User data is not Cached
        sanitized.setUsername(null);
        sanitized.setDisplayName(null);
        sanitized.setProfilePicture(null);
        sanitized.setIsLiked(null);
        // Avoiding cache on nested repost payload to keep cache strictly post-only and small
        sanitized.setRepostPostData(null);

        return sanitized;
    }

    public void cachePostData(PostData postData) {
        Long userId = postData.getUserId();
        String userIndexKey = "user:posts:" + userId;

        logger.debug("Caching post data for ID: {}", postData.getId());

        PostData toCache = sanitizeForCache(postData);
        redisTemplateForPostData.opsForValue().set(POST_ID_DATA_KEY_PREFIX + postData.getId(), toCache, Duration.ofHours(1));
        redisTemplateForSet.opsForSet().add(userIndexKey, postData.getId());
        redisTemplateForSet.expire(userIndexKey, Duration.ofHours(1));

        logger.debug("Post data cached successfully for ID: {} and added to user index for userId: {}", postData.getId(), userId);
    }

    public PostData getCachedPostById(Long postId) {
        logger.debug("Fetching post data for ID on Cache: {}", postId);
        PostData post = redisTemplateForPostData.opsForValue().get(POST_ID_DATA_KEY_PREFIX + postId);
        if(post != null) {
            logger.debug("CACHE HIT: post data found for ID: {}", postId);
            redisTemplateForPostData.expire(POST_ID_DATA_KEY_PREFIX + postId, Duration.ofHours(1));
            return post;
        }
        logger.debug("CACHE MISS: post data not found for ID: {}", postId);
        return null;
    }

    public boolean existsPostById(Long postId) {
        logger.debug("Checking existence of post data for ID: {}", postId);
        return redisTemplateForPostData.hasKey(POST_ID_DATA_KEY_PREFIX + postId);
    }

    public void deletePostData(PostData postData) {
        String lockKey = "post:" + postData.getId();
        withLock(lockKey, () -> {
            Long userId = postData.getUserId();
            logger.debug("Deleting post data for ID: {}", userId);
            String userIndexKey = String.format("user:posts:%s", userId);


            PostData toCache = sanitizeForCache(postData);
            redisTemplateForPostData.opsForValue().set(POST_ID_DATA_KEY_PREFIX + postData.getId(), toCache, Duration.ofHours(1));

            redisTemplateForSet.opsForSet().remove(userIndexKey, postData.getId());
        });
    }

    public Set<Long> getUserPostIds(Long userId) {
        String userIndexKey = "user:posts:" + userId;
        logger.debug("Fetching post IDs for user ID: {}", userId);
        return redisTemplateForSet.opsForSet().members(userIndexKey);
    }

    public Boolean getLikeStatus(Long userId, Long postId) {
        String likeKey = LIKE_KEY_PREFIX + userId + ":post:" + postId;
        logger.debug("Fetching like status for user ID: {} and post ID: {}", userId, postId);
        Boolean liked = redisTemplateForLike.opsForValue().get(likeKey);
        if (liked != null) {
            redisTemplateForLike.expire(likeKey, Duration.ofHours(1));
            logger.debug("CACHE HIT: like status found for user {} post {}: {}", userId, postId, liked);
        } else {
            logger.debug("CACHE MISS: like status not found for user {} post {}", userId, postId);
        }
        return liked;
    }

    public void setLikeStatus(Long userId, Long postId, boolean liked) {
        String likeKey = LIKE_KEY_PREFIX + userId + ":post:" + postId;
        logger.debug("Setting like status for user ID: {} and post ID: {} to {}", userId, postId, liked);
        redisTemplateForLike.opsForValue().set(likeKey, liked, Duration.ofHours(1));
    }

    public Long getCachedUserIdByUsername(String username) {
        String cacheKey = "user:username:" + username;
        Number rawValue = redisTemplateForLong.opsForValue().get(cacheKey);
        return rawValue != null ? rawValue.longValue() : null;
    }

    public User getCachedUserById(Long userId) {
        String cacheKey = "user:id:" + userId;
        return redisTemplateForUser.opsForValue().get(cacheKey);
    }

    public UserPublicData getCachedUserPublicData(Long userId) {
        String cacheKey = "user:public:" + userId;
        return redisTemplateForUserPublicData.opsForValue().get(cacheKey);
    }

    private void withLock(String lockKey, Runnable action) {
        Lock lock = redisLockRegistry.obtain(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(1, TimeUnit.SECONDS);
            if (locked) {
                action.run();
            } else {
                logger.debug("Failed to acquire lock for key: {}", lockKey);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while acquiring lock for key: {}", lockKey, e);
        } finally {
            if (locked) {
                lock.unlock();
            }
        }
    }

    public boolean trySetLock(String key, String value, long timeoutSeconds) {
        return redisTemplateForString.opsForValue().setIfAbsent(key, value, timeoutSeconds, TimeUnit.SECONDS);
    }

    public void deleteLock(String key) {
        redisTemplateForString.delete(key);
    }
}
