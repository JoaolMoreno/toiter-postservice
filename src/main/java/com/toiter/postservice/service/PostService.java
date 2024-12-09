package com.toiter.postservice.service;

import com.toiter.postservice.entity.Post;
import com.toiter.postservice.entity.View;
import com.toiter.postservice.model.*;
import com.toiter.postservice.producer.KafkaProducer;
import com.toiter.postservice.repository.PostRepository;
import com.toiter.postservice.repository.ViewRepository;
import jakarta.validation.constraints.NotNull;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class PostService {

    private final UserClientService userClientService;
    private final PostRepository postRepository;
    private final KafkaProducer kafkaProducer;
    private final Logger logger = LoggerFactory.getLogger(PostService.class);
    private final String POST_ID_DATA_KEY_PREFIX = "post:id:";
    private final String POST_PARENTID_DATA_KEY_PREFIX = "post:parentid:";
    private final String POST_REPOSTID_DATA_KEY_PREFIX = "post:repostid:";
    private final RedisTemplate<String, PostData> redisTemplateForPostData;
    private final ViewRepository viewRepository;
    private final LikeService likeService;

    public PostService(UserClientService userClientService, PostRepository postRepository, KafkaProducer kafkaProducer, RedisTemplate<String, PostData> redisTemplateForPostData, ViewRepository viewRepository, LikeService likeService) {
        this.userClientService = userClientService;
        this.postRepository = postRepository;
        this.kafkaProducer = kafkaProducer;
        this.redisTemplateForPostData = redisTemplateForPostData;
        this.viewRepository = viewRepository;
        this.likeService = likeService;
    }

    @Transactional
    public Post createPost(PostRequest post, Long userId) {
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
            return newPost;
        }
        catch (Exception e) {
            logger.error("Failed to send event to Kafka", e);
            throw new RuntimeException("Failed to send event to Kafka");
        }
    }

    public Optional<PostData> getPostById(Long id, int depth, Long userId) {
        logger.debug("Fetching post data for ID: {}, depth: {}", id, depth);
        PostData postData;
        postData = redisTemplateForPostData.opsForValue().get(POST_ID_DATA_KEY_PREFIX + id);
        if (postData != null) {
            logger.debug("Post data found in cache for ID: {}", id);
            redisTemplateForPostData.expire(POST_ID_DATA_KEY_PREFIX + id, Duration.ofHours(1));
            if(postData.isDeleted()){
                return Optional.empty();
            }
            if(userId != null){
                postData.setIsLiked(likeService.userLikedPost(userId, id));
            }
            return Optional.of(postData);
        }
        logger.debug("Post data not found in cache for ID: {}", id);
        Optional<PostData> post = postRepository.fetchPostData(id);
        if (post.isPresent()) {
            if(post.get().isDeleted()){
                return Optional.empty();
            }

            String username = userClientService.getUsernameById(post.get().getUserId());
            post.get().setUsername(username);

            if(post.get().getRepostParentId() != null && depth == 0){
                Optional<PostData> repostedPostData = getPostById(post.get().getRepostParentId(), depth - 1, userId);
                repostedPostData.ifPresent(post.get()::setRepostPostData);
            }

            logger.debug("Post data found in database for ID: {}", id);
            redisTemplateForPostData.opsForValue().set(POST_ID_DATA_KEY_PREFIX + id, post.get(), Duration.ofHours(1));
            post.get().setIsLiked(
                    likeService.userLikedPost(userId, id)
            );
            return post;
        }
        logger.debug("Post data not found in database for ID: {}", id);
        return Optional.empty();
    }

    public Page<PostData> getPostsByUser(String username, Pageable pageable) {
        logger.debug("Fetching posts by username: {}", username);
        Long userId = userClientService.getUserIdByUsername(username);
        Page<PostData> posts = postRepository.fetchPostsByUserId(userId, pageable);
        if (!posts.isEmpty()) {
            posts.stream().forEach(post -> {
                post.setUsername(username);
                post.setIsLiked(likeService.userLikedPost(userId, post.getId()));
            });
        }
        return posts;
    }

    public Page<PostData> getPostsByParentPostId(Long parentPostId, Pageable pageable, Long userId) {
        logger.debug("Fetching posts by parent post ID: {}", parentPostId);
        String postParentDataKey = POST_PARENTID_DATA_KEY_PREFIX + parentPostId;
        long start = pageable.getOffset();
        long end = start + pageable.getPageSize() - 1;

        // Buscar os posts filhos do Redis
        List<PostData> cachedPosts = redisTemplateForPostData.opsForList().range(postParentDataKey, start, end);

        // Se a quantidade de posts no Redis for insuficiente, buscar no banco
        if (cachedPosts == null || cachedPosts.size() < pageable.getPageSize()) {
            Page<PostData> posts = postRepository.fetchChildPostsData(parentPostId, pageable);
            if(posts.isEmpty()){
                return posts;
            }
            posts.stream().forEach(post -> {
                String username = userClientService.getUsernameById(post.getUserId());
                post.setUsername(username);
                post.setIsLiked(likeService.userLikedPost(userId, post.getId()));
            });

            // Adicionar os posts ao cache
            redisTemplateForPostData.delete(postParentDataKey);
            redisTemplateForPostData.opsForList().rightPushAll(postParentDataKey, posts.getContent());
            redisTemplateForPostData.expire(postParentDataKey, Duration.ofHours(1));

            return posts;
        }

        // Retornar os dados do Redis como um Page
        cachedPosts.forEach(post -> {
            post.setIsLiked(likeService.userLikedPost(userId, post.getId()));
        });
        Long totalElements = redisTemplateForPostData.opsForList().size(postParentDataKey);
        totalElements = (totalElements != null) ? totalElements : 0;

        return new PageImpl<>(cachedPosts, pageable, totalElements);
    }

    public void deletePost(Long id) {
        logger.debug("Deleting post with ID: {}", id);
        Post post = postRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        post.setContent(null);
        post.setUserId(null);
        post.setMediaUrl(null);
        post.setDeletedAt(LocalDateTime.now());
        post.setDeleted(true);

        postRepository.save(post);

        try {
            PostCreatedEvent event = new PostCreatedEvent(post);
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

        if(childPostsPage.isEmpty()){
            return new PostThread(parentPost, List.of(), false, 0, 0, 0, 0);
        }

        List<PostThread.ChildPost> childPostsWithIds = childPostsPage.getContent().stream()
                .map(childPost -> {
                    List<Long> childIds = getChildPostIds(childPost.getId());
                    return new PostThread.ChildPost(childPost, childIds);
                })
                .toList();

        boolean hasNext = childPostsPage.hasNext();
        long totalElements = childPostsPage.getTotalElements();
        int totalPages = childPostsPage.getTotalPages();
        int pageSize = childPostsPage.getSize();
        int currentPage = childPostsPage.getNumber();

        return new PostThread(parentPost, childPostsWithIds, hasNext, totalElements, totalPages, pageSize, currentPage);
    }


    private List<Long> getChildPostIds(Long parentPostId) {
        logger.debug("Fetching child post IDs for parent post ID: {}", parentPostId);
        String childIdsKey = "post:childids:" + parentPostId;

        List<PostData> cachedChildPosts = redisTemplateForPostData.opsForList().range(childIdsKey, 0, -1);
        List<Long> childIds;

        if (cachedChildPosts != null && !cachedChildPosts.isEmpty()) {
            redisTemplateForPostData.expire(childIdsKey, Duration.ofHours(1));
            return cachedChildPosts.stream().map(PostData::getId).toList();
        }

        childIds = postRepository.findChildIdsByParentPostId(parentPostId,Pageable.ofSize(3));

        // TODO: enviar um evento para popular o cache em segundo plano passando childIds
        return childIds;
    }

    @Transactional
    public void viewPost(@NotNull(message = "Post ID cant be NULL") Long postId, Long userId) {
        logger.debug("Viewing post with ID: {} by user ID: {}", postId, userId);
        String redisKey = POST_ID_DATA_KEY_PREFIX + postId;

        PostData postData = redisTemplateForPostData.opsForValue().get(redisKey);

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

        } catch (DataIntegrityViolationException _) {
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
}

