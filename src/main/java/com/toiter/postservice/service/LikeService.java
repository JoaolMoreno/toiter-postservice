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
import org.springframework.stereotype.Service;

@Service
public class LikeService {
    private final LikeRepository likeRepository;
    private final Logger logger = LoggerFactory.getLogger(LikeService.class);
    private final CacheService cacheService;
    private final KafkaProducer kafkaProducer;

    public LikeService(LikeRepository likeRepository, CacheService cacheService, KafkaProducer kafkaProducer) {
        this.likeRepository = likeRepository;
        this.cacheService = cacheService;
        this.kafkaProducer = kafkaProducer;
    }

    @Transactional
    public void likePost(Long postId, Long userId) {
        logger.debug("Liking post with ID: {} by user ID: {}", postId, userId);

        PostData postData = cacheService.getCachedPostById(postId);

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

        cacheService.setLikeStatus(userId, postId, true);
        kafkaProducer.sendLikedEvent(new PostLikedEvent(postId, userId));

        logger.debug("Post with ID: {} liked by user ID: {}", postId, userId);

    }

    @Transactional
    public void unlikePost(Long postId, Long userId) {
        logger.debug("Unliking post with ID: {} by user ID: {}", postId, userId);

        PostData postData = cacheService.getCachedPostById(postId);

        if (postData != null && postData.isDeleted()) {
            logger.error("Post with ID: {} is deleted and cannot have likes removed.", postId);
            throw new IllegalStateException("O post foi deletado e não pode ter curtida removida.");
        }

        try {
            boolean likeExisted = likeRepository.existsByUserIdAndPostId(userId, postId);
            if (likeExisted) {
                likeRepository.deleteByPostIdAndUserId(postId, userId);
                cacheService.setLikeStatus(userId, postId, false);
                kafkaProducer.sendLikedEvent(new PostUnlikedEvent(postId, userId));
                logger.debug("Post with ID: {} unliked by user ID: {}", postId, userId);
            } else {
                logger.warn("Like not found for post ID: {} and user ID: {}", postId, userId);
            }
        } catch (ResourceNotFoundException e) {
        }
    }

    public boolean userLikedPost(Long userId, Long postId) {
        return userLikedPost(userId, postId, false);
    }

    private boolean userLikedPost(Long userId, Long postId, boolean isRetry) {
        if (!isRetry) {
            logger.debug("Checking if user {} liked post {}", userId, postId);
        }
        Boolean liked = cacheService.getLikeStatus(userId, postId);
        if (liked != null) {
            return liked;
        }
        if (!isRetry) {
            logger.debug("Like status not in cache for user {} post {}", userId, postId);
        }

        String lockKey = "lock:like:" + userId + ":" + postId;
        if (cacheService.trySetLock(lockKey, "1", 10)) {
            liked = cacheService.getLikeStatus(userId, postId);
            if (liked != null) {
                cacheService.deleteLock(lockKey);
                return liked;
            }
            boolean existsInDb = likeRepository.existsByUserIdAndPostId(userId, postId);
            cacheService.setLikeStatus(userId, postId, existsInDb);
            cacheService.deleteLock(lockKey);
            return existsInDb;
        } else {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Interrupted while waiting for lock on like: user {} post {}", userId, postId, e);
                return false;
            }
            return userLikedPost(userId, postId, true);
        }
    }
}
