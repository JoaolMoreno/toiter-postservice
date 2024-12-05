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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class LikeService {
    private final LikeRepository likeRepository;
    private final Logger logger = LoggerFactory.getLogger(LikeService.class);
    private final String POST_ID_DATA_KEY_PREFIX = "post:id:";
    private final RedisTemplate<String, PostData> redisTemplateForPostData;
    private final KafkaProducer kafkaProducer;

    public LikeService(LikeRepository likeRepository, RedisTemplate<String, PostData> redisTemplateForPostData, KafkaProducer kafkaProducer) {
        this.likeRepository = likeRepository;
        this.redisTemplateForPostData = redisTemplateForPostData;
        this.kafkaProducer = kafkaProducer;
    }

    @Transactional
    public void likePost(Long postId, Long userId) {
        String redisKey = POST_ID_DATA_KEY_PREFIX + postId;

        // Buscar PostData do Redis
        PostData postData = redisTemplateForPostData.opsForValue().get(redisKey);

        // Verificar se o post foi deletado
        if (postData != null && postData.isDeleted()) {
            throw new ResourceNotFoundException("O post foi deletado e não pode ser curtido.");
        }

        // Criar e salvar o objeto Like no banco de dados
        try {
            Like like = new Like(
                    postId,
                    userId
            );
            likeRepository.save(like);

            kafkaProducer.sendLikedEvent(new PostLikedEvent(postId, userId));

        } catch (DataIntegrityViolationException _) {
        }
    }

    @Transactional
    public void unlikePost(Long postId, Long userId) {
        String redisKey = POST_ID_DATA_KEY_PREFIX + postId;

        // Buscar PostData do Redis
        PostData postData = redisTemplateForPostData.opsForValue().get(redisKey);

        // Verificar se o post foi deletado
        if (postData != null && postData.isDeleted()) {
            throw new IllegalStateException("O post foi deletado e não pode ter curtida removida.");
        }

        // Deletar o objeto Like do banco de dados
        try {
            likeRepository.deleteByPostIdAndUserId(postId, userId);
            kafkaProducer.sendLikedEvent(new PostUnlikedEvent(postId, userId));
        } catch (ResourceNotFoundException _) {
        }
    }
}
