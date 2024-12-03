package com.toiter.postservice.service;

import com.toiter.postservice.entity.Post;
import com.toiter.postservice.model.PostCreatedEvent;
import com.toiter.postservice.model.PostData;
import com.toiter.postservice.model.PostRequest;
import com.toiter.postservice.producer.KafkaProducer;
import com.toiter.postservice.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
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
                userId,
                post.content(),
                post.mediaUrl()
        );
        postRepository.save(newPost);

        PostCreatedEvent event = new PostCreatedEvent(
                newPost.getId(),
                newPost.getUserId(),
                newPost.getContent(),
                newPost.getParentPostId(),
                newPost.getRepostParentId()
        );
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
        PostData postData;
        postData = redisTemplateForPostData.opsForValue().get(POST_ID_DATA_KEY_PREFIX + id);
        if (postData != null) {
            return Optional.of(postData);
        }
        Optional<Post> post =  postRepository.findById(id);
        if (post.isPresent()) {
            postData = new PostData(post.get());
            redisTemplateForPostData.opsForValue().set(POST_ID_DATA_KEY_PREFIX + id, postData, Duration.ofHours(1));
            return Optional.of(postData);
        }
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

        List<PostData> cachedPosts = redisTemplateForPostData.opsForList().range(postParentDataKey, start, end);

        if (cachedPosts == null || cachedPosts.isEmpty()) {
            Page<Post> dbPosts = postRepository.findByParentPostId(parentPostId, pageable);
            List<PostData> postsToCache = dbPosts.getContent().stream().map(PostData::new).toList();

            for (PostData post : postsToCache) {
                redisTemplateForPostData.opsForList().rightPush(postParentDataKey, post);
            }
            redisTemplateForPostData.expire(postParentDataKey, Duration.ofHours(1));

            return dbPosts.map(PostData::new);
        }

        Long size = redisTemplateForPostData.opsForList().size(postParentDataKey);
        size = (size != null) ? size : 0;

        return new PageImpl<>(cachedPosts, pageable, size);
    }


    public void deletePost(Long id) {
        try {
            postRepository.deleteById(id);
        } catch (EmptyResultDataAccessException e) {
            logger.warn("Post with id {} does not exist", id);
        }
    }
}

