package com.toiter.postservice.service;

import com.toiter.postservice.entity.Post;
import com.toiter.postservice.entity.View;
import com.toiter.postservice.model.*;
import com.toiter.postservice.producer.KafkaProducer;
import com.toiter.postservice.repository.PostRepository;
import com.toiter.postservice.repository.ViewRepository;
import com.toiter.userservice.model.UserResponse;
import jakarta.validation.constraints.NotNull;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class PostService {

    private final UserClientService userClientService;
    private final PostRepository postRepository;
    private final KafkaProducer kafkaProducer;
    private final Logger logger = LoggerFactory.getLogger(PostService.class);
    private final ViewRepository viewRepository;
    private final LikeService likeService;
    private final CacheService cacheService;

    public PostService(UserClientService userClientService, PostRepository postRepository, KafkaProducer kafkaProducer, ViewRepository viewRepository, LikeService likeService, CacheService cacheService) {
        this.userClientService = userClientService;
        this.postRepository = postRepository;
        this.kafkaProducer = kafkaProducer;
        this.viewRepository = viewRepository;
        this.likeService = likeService;
        this.cacheService = cacheService;
    }

    @Transactional
    public PostData createPost(PostRequest post, Long userId) {
        logger.debug("Creating post for user ID: {}", userId);
        if(post.content() == null || post.content().isEmpty()){
            if(post.repostParentId() == null){
                throw new IllegalArgumentException("Content can only be empty for reposts");
            }
        }

        if(post.repostParentId() != null) {
            if (post.parentPostId() != null) {
                throw new IllegalArgumentException("A repost cannot have a parent post");
            }
        }

        Post newPost = new Post(
                post.parentPostId(),
                post.repostParentId(),
                userId,
                post.content(),
                post.mediaUrl()
        );
        postRepository.save(newPost);

        PostCreatedEvent event = new PostCreatedEvent(newPost);
        try {
            kafkaProducer.sendPostCreatedEvent(event);
            PostData postData = new PostData(newPost);

            // Enrich only for response (cache will store sanitized copy)
            UserResponse userResponse = userClientService.getUserById(userId);
            postData.setUsername(userResponse.getUsername());
            postData.setDisplayName(userResponse.getDisplayName());
            String userProfilePicture = userResponse.getProfileImageUrl();
            postData.setProfilePicture(userProfilePicture);

            postData.setIsLiked(false);

            if(postData.getRepostParentId() != null){
                Optional<PostData> repostedPostData = getPostById(postData.getRepostParentId(), -1, userId);
                repostedPostData.ifPresent(postData::setRepostPostData);
            }

            try {
                cacheService.cachePostData(postData);
            } catch (Exception e) {
                logger.error("Failed to cache post data for post ID: {}", newPost.getId(), e);
            }

            return postData;
        }
        catch (Exception e) {
            logger.error("Failed to send event to Kafka", e);
            throw new RuntimeException("Failed to send event to Kafka");
        }
    }

    public Optional<PostData> getPostById(Long id, int depth, Long userId) {
        return getPostById(id, depth, userId, false);
    }

    private Optional<PostData> getPostById(Long id, int depth, Long userId, boolean isRetry) {
        if (!isRetry) {
            logger.debug("Fetching post data for ID: {}, depth: {}", id, depth);
        }
        PostData postData = cacheService.getCachedPostById(id);
        if (postData != null) {
            logger.debug("Post data found in cache for ID: {}", id);
            PostData enriched = enrichPostData(postData, userId, depth);
            return enriched == null ? Optional.empty() : Optional.of(enriched);
        }
        if (!isRetry) {
            logger.debug("Post data not found in cache for ID: {}", id);
        }

        String lockKey = "lock:post:" + id;
        if (cacheService.trySetLock(lockKey, "1", 10)) {
            Optional<PostData> post = postRepository.fetchPostData(id);
            if (post.isPresent()) {
                if (post.get().isDeleted()) {
                    cacheService.deleteLock(lockKey);
                    return Optional.empty();
                }

                cacheService.cachePostData(post.get());
                cacheService.deleteLock(lockKey);

                UserResponse userResponse = userClientService.getUserById(post.get().getUserId());
                post.get().setUsername(userResponse.getUsername());
                post.get().setDisplayName(userResponse.getDisplayName());

                if (post.get().getRepostParentId() != null && depth == 0) {
                    Optional<PostData> repostedPostData = getPostById(post.get().getRepostParentId(), depth - 1, userId, false);
                    repostedPostData.ifPresent(post.get()::setRepostPostData);
                }

                logger.debug("Post data found in database for ID: {}", id);

                String userProfilePicture = userResponse.getProfileImageUrl();
                post.get().setProfilePicture(userProfilePicture);

                post.get().setIsLiked(
                        likeService.userLikedPost(userId, id)
                );
                return post;
            }
            cacheService.deleteLock(lockKey);
            logger.debug("Post data not found in database for ID: {}", id);
            return Optional.empty();
        } else {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Interrupted while waiting for lock on post ID: {}", id, e);
                return Optional.empty();
            }
            return getPostById(id, depth, userId, true);
        }
    }

    public Page<PostData> getPostsByUser(String username, Long authenticatedUserId, Pageable pageable) {
        logger.debug("Fetching posts by username: {}", username);
        Long userId = userClientService.getUserIdByUsername(username);
        Page<Long> postIds = postRepository.fetchIdsByUserId(userId, pageable);
        List<PostData> posts = postIds.stream()
                .map(postId -> getPostById(postId, 0, authenticatedUserId))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
        return new PageImpl<>(posts, pageable, postIds.getTotalElements());
    }

    public Page<PostData> getPostsByParentPostId(Long parentPostId, Pageable pageable, Long userId) {
        logger.debug("Fetching posts by parent post ID: {}", parentPostId);
        Page<Long> postIds = postRepository.findChildIdsByParentPostId(parentPostId, pageable);
        List<PostData> posts = postIds.stream()
                .map(postId -> getPostById(postId, 0, userId))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
        return new PageImpl<>(posts, pageable, postIds.getTotalElements());
    }

    public void deletePost(Long id, Long userId) {
        logger.debug("Deleting post with ID: {}", id);
        Post post = postRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        Long postUserId = post.getUserId();

        if (!postUserId.equals(userId)) {
            throw new IllegalArgumentException("Usuário não tem permissão para deletar este post");
        }
        post.setContent("");
        post.setMediaUrl(null);
        post.setDeletedAt(LocalDateTime.now());
        post.setDeleted(true);

        postRepository.save(post);

        try {
            PostDeletedEvent event = new PostDeletedEvent(post);
            kafkaProducer.sendPostDeletedEvent(event);
        }
        catch (Exception e) {
            logger.error("Failed to send event to Kafka", e);
            throw new RuntimeException("Failed to send event to Kafka");
        }
    }

    public PostThread getPostThread(Long parentPostId, Pageable pageable, Long userId) {
        logger.debug("Fetching post thread for parent post ID: {}", parentPostId);
        PostData parentPost = getPostById(parentPostId, 0, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent post not found"));

        Page<PostData> childPostsPage = getPostsByParentPostId(parentPostId, pageable, userId);

        if (childPostsPage.isEmpty()) {
            return new PostThread(parentPost, List.of(), false, 0, 0, 0, 0);
        }

        List<PostThread.ChildPost> childPostsWithIds = childPostsPage.getContent().stream()
                .map(childPost -> {
                    List<PostData> childPosts = getPostsByParentPostId(childPost.getId(), Pageable.unpaged(), userId).getContent();
                    return new PostThread.ChildPost(childPost, childPosts);
                })
                .toList();

        boolean hasNext = childPostsPage.hasNext();
        long totalElements = childPostsPage.getTotalElements();
        int totalPages = childPostsPage.getTotalPages();
        int pageSize = childPostsPage.getSize();
        int currentPage = childPostsPage.getNumber();

        return new PostThread(parentPost, childPostsWithIds, hasNext, totalElements, totalPages, pageSize, currentPage);
    }

    @Transactional
    public void viewPost(@NotNull(message = "Post ID cant be NULL") Long postId, Long userId) {
        logger.debug("Viewing post with ID: {} by user ID: {}", postId, userId);

        PostData postData = cacheService.getCachedPostById(postId);

        if (postData != null && postData.isDeleted()) {
            return;
        }

        try {
            View view = new View(
                    postId,
                    userId
            );
            viewRepository.save(view);

            kafkaProducer.sendPostViewedEvent(new PostViewedEvent(postId, userId));

        } catch (DataIntegrityViolationException e) {
        }
    }

    public Page<PostData> getPosts(Pageable pageable, Long userId) {
        logger.debug("Fetching all posts");
        Page<Long> postIds = postRepository.fetchAllPostIds(pageable);
        List<PostData> posts = postIds.stream()
                .map(postId -> getPostById(postId, 0, userId))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
        return new PageImpl<>(posts, pageable, postIds.getTotalElements());
    }

    public Integer getPostsCount(Long userId) {
        logger.debug("Fetching posts count for user ID: {}", userId);
        return postRepository.countByUserId(userId);
    }

    private PostData enrichPostData(PostData postData, Long userId, int depth) {
        if (postData.isDeleted()) {
            return null;
        }
        UserResponse userResponse = userClientService.getUserById(postData.getUserId());
        postData.setUsername(userResponse.getUsername());
        postData.setDisplayName(userResponse.getDisplayName());
        postData.setProfilePicture(userResponse.getProfileImageUrl());
        if (userId != null) {
            postData.setIsLiked(likeService.userLikedPost(userId, postData.getId()));
        }
        if (postData.getRepostParentId() != null && depth == 0) {
            Optional<PostData> repostedPostData = getPostById(postData.getRepostParentId(), depth - 1, userId);
            repostedPostData.ifPresent(postData::setRepostPostData);
        }
        return postData;
    }
}
