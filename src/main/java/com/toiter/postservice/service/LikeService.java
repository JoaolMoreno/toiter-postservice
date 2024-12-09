package com.toiter.postservice.service;

import com.toiter.postservice.entity.Like;
import com.toiter.postservice.model.PostData;
import com.toiter.postservice.model.PostLikedEvent;
import com.toiter.postservice.model.PostUnlikedEvent;
import com.toiter.postservice.producer.KafkaProducer;
import com.toiter.postservice.repository.LikeRepository;
import jakarta.transaction.Transactional;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class LikeService {
    private final LikeRepository likeRepository;
    private final Logger logger = LoggerFactory.getLogger(LikeService.class);
    private final String POST_ID_DATA_KEY_PREFIX = "post:id:";
    private static final String LIKE_KEY_PREFIX = "like:user:";
    private final RedisTemplate<String, PostData> redisTemplateForPostData;
    private final RedisTemplate<String, Boolean> redisTemplateForLike;
    private final KafkaProducer kafkaProducer;

    public LikeService(LikeRepository likeRepository, RedisTemplate<String, PostData> redisTemplateForPostData, RedisTemplate<String, Boolean> redisTemplateForLike, KafkaProducer kafkaProducer) {
        this.likeRepository = likeRepository;
        this.redisTemplateForPostData = redisTemplateForPostData;
        this.redisTemplateForLike = redisTemplateForLike;
        this.kafkaProducer = kafkaProducer;
    }

    @Transactional
    public void likePost(Long postId, Long userId) {
        logger.debug("Liking post with ID: {} by user ID: {}", postId, userId);
        String redisKey = POST_ID_DATA_KEY_PREFIX + postId;

        PostData postData = redisTemplateForPostData.opsForValue().get(redisKey);

        if (postData != null && postData.isDeleted()) {
            logger.error("Post with ID: {} is deleted and cannot be liked.", postId);
            throw new ResourceNotFoundException("O post foi deletado e não pode ser curtido.");
        }

        if(likeRepository.existsByUserIdAndPostId(userId, postId)) {
            logger.warn("User ID: {} already liked post ID: {}", userId, postId);
            return;
        }

        Like like = new Like(
                postId,
                userId
        );
        likeRepository.save(like);

        kafkaProducer.sendLikedEvent(new PostLikedEvent(postId, userId));

        logger.debug("Post with ID: {} liked by user ID: {}", postId, userId);

    }

    @Transactional
    public void unlikePost(Long postId, Long userId) {
        logger.debug("Unliking post with ID: {} by user ID: {}", postId, userId);
        String redisKey = POST_ID_DATA_KEY_PREFIX + postId;

        PostData postData = redisTemplateForPostData.opsForValue().get(redisKey);

        if (postData != null && postData.isDeleted()) {
            logger.error("Post with ID: {} is deleted and cannot have likes removed.", postId);
            throw new IllegalStateException("O post foi deletado e não pode ter curtida removida.");
        }

        try {
            boolean likeExisted = likeRepository.existsByUserIdAndPostId(userId, postId);
            if (likeExisted) {
                likeRepository.deleteByPostIdAndUserId(postId, userId);
                kafkaProducer.sendLikedEvent(new PostUnlikedEvent(postId, userId));
                logger.debug("Post with ID: {} unliked by user ID: {}", postId, userId);
            } else {
                logger.warn("Like not found for post ID: {} and user ID: {}", postId, userId);
            }
        } catch (ResourceNotFoundException _) {
        }
    }

    public boolean userLikedPost(Long userId, Long postId) {
        String likeKey = generateLikeKey(userId, postId);

        Boolean liked = redisTemplateForLike.opsForValue().get(likeKey);
        if (liked != null) {
            redisTemplateForLike.expire(likeKey, Duration.ofHours(1));
            return liked;
        }

        boolean existsInDb = likeRepository.existsByUserIdAndPostId(userId, postId);
        
        redisTemplateForLike.opsForValue().set(likeKey, existsInDb, Duration.ofHours(1));

        return existsInDb;
    }

    public void registerLikeInRedis(Long userId, Long postId) {
        String likeKey = generateLikeKey(userId, postId);
        logger.debug("Registering like in Redis for user ID: {} and post ID: {}", userId, postId);
        redisTemplateForLike.opsForValue().set(likeKey, true, Duration.ofHours(1));
    }

    public void removeLikeFromRedis(Long userId, Long postId) {
        String likeKey = generateLikeKey(userId, postId);
        logger.debug("Removing like from Redis for user ID: {} and post ID: {}", userId, postId);
        redisTemplateForLike.opsForValue().set(likeKey, false, Duration.ofHours(1));
    }

    private String generateLikeKey(Long userId, Long postId) {
        return LIKE_KEY_PREFIX + userId + ":post:" + postId;
    }
}
