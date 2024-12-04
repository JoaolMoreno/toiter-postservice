package com.toiter.postservice.service;

import com.toiter.postservice.entity.Post;
import com.toiter.postservice.model.PostCreatedEvent;
import com.toiter.postservice.model.PostData;
import com.toiter.postservice.model.PostRequest;
import com.toiter.postservice.model.PostThread;
import com.toiter.postservice.producer.KafkaProducer;
import com.toiter.postservice.repository.PostRepository;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public PostService(UserClientService userClientService, PostRepository postRepository, KafkaProducer kafkaProducer, RedisTemplate<String, PostData> redisTemplateForPostData) {
        this.userClientService = userClientService;
        this.postRepository = postRepository;
        this.kafkaProducer = kafkaProducer;
        this.redisTemplateForPostData = redisTemplateForPostData;
    }

    @Transactional
    public Post createPost(PostRequest post, Long userId) {
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

    public Optional<PostData> getPostById(Long id) {
        logger.debug("Fetching post data for ID: {}", id);
        PostData postData;
        postData = redisTemplateForPostData.opsForValue().get(POST_ID_DATA_KEY_PREFIX + id);
        if (postData != null) {
            logger.debug("Post data found in cache for ID: {}", id);
            redisTemplateForPostData.expire(POST_ID_DATA_KEY_PREFIX + id, Duration.ofHours(1));
            return Optional.of(postData);
        }
        logger.debug("Post data not found in cache for ID: {}", id);
        Optional<PostData> post = postRepository.fetchPostData(id);
        if (post.isPresent()) {
            logger.debug("Post data found in database for ID: {}", id);
            redisTemplateForPostData.opsForValue().set(POST_ID_DATA_KEY_PREFIX + id, post.get(), Duration.ofHours(1));
            return post;
        }
        logger.debug("Post data not found in database for ID: {}", id);
        return Optional.empty();
    }

    public Page<PostData> getPostsByUser(String username, Pageable pageable) {
        Long userId = userClientService.getUserIdByUsername(username);
        Page<Post> post = postRepository.findByUserId(userId, pageable);
        return post.map(PostData::new);
    }

    public Page<PostData> getPostsByParentPostId(Long parentPostId, Pageable pageable) {
        String postParentDataKey = POST_PARENTID_DATA_KEY_PREFIX + parentPostId;
        long start = pageable.getOffset();
        long end = start + pageable.getPageSize() - 1;

        // Buscar os posts filhos do Redis
        List<PostData> cachedPosts = redisTemplateForPostData.opsForList().range(postParentDataKey, start, end);

        // Se a quantidade de posts no Redis for insuficiente, buscar no banco
        if (cachedPosts == null || cachedPosts.size() < pageable.getPageSize()) {
            Page<PostData> posts = postRepository.fetchChildPostsData(parentPostId, pageable);

            // Adicionar os posts ao cache
            redisTemplateForPostData.delete(postParentDataKey);
            redisTemplateForPostData.opsForList().rightPushAll(postParentDataKey, posts.getContent());
            redisTemplateForPostData.expire(postParentDataKey, Duration.ofHours(1));

            return posts;
        }

        // Retornar os dados do Redis como um Page
        Long totalElements = redisTemplateForPostData.opsForList().size(postParentDataKey);
        totalElements = (totalElements != null) ? totalElements : 0;

        return new PageImpl<>(cachedPosts, pageable, totalElements);
    }

    public void deletePost(Long id) {
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

    public PostThread getPostThread(Long parentPostId, Pageable pageable) {
        PostData parentPost = getPostById(parentPostId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent post not found"));

        Page<PostData> childPostsPage = getPostsByParentPostId(parentPostId, pageable);

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
}

