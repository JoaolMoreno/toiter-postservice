package com.toiter.postservice.service;

import com.toiter.postservice.entity.Post;
import com.toiter.postservice.model.PostCreatedEvent;
import com.toiter.postservice.model.PostRequest;
import com.toiter.postservice.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class PostService {

    private final UserClientService userClientService;
    private final PostRepository postRepository;
    private final KafkaTemplate<String, PostCreatedEvent> kafkaTemplate;
    private final Logger logger = LoggerFactory.getLogger(PostService.class);

    public PostService(UserClientService userClientService, PostRepository postRepository, KafkaTemplate<String, PostCreatedEvent> kafkaTemplate) {
        this.userClientService = userClientService;
        this.postRepository = postRepository;
        this.kafkaTemplate = kafkaTemplate;
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
                newPost.getParentPostId()
        );
        try {
            kafkaTemplate.send("post-created-topic", event);
            return newPost;
        }
        catch (Exception e) {
            logger.error("Failed to send event to Kafka", e);
            throw new RuntimeException("Failed to send event to Kafka");
        }
    }

    public Optional<Post> getPostById(Long id) {
        return postRepository.findById(id);
    }

    public Page<Post> getPostsByUser(String username, Pageable pageable) {
        Long userId = userClientService.getUserIdByUsername(username);
        return postRepository.findByUserId(userId, pageable);
    }

    public Page<Post> getPostsByParentPostId(Long parentPostId, Pageable pageable) {
        return postRepository.findByParentPostId(parentPostId, pageable);
    }

    public void deletePost(Long id) {
        try {
            postRepository.deleteById(id);
        } catch (EmptyResultDataAccessException e) {
            logger.warn("Post with id {} does not exist", id);
        }
    }
}

