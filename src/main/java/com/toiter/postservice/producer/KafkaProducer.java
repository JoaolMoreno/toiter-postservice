package com.toiter.postservice.producer;

import com.toiter.postservice.model.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaProducer {
    private final KafkaTemplate<String, PostEvent> kafkaTemplate;
    private final KafkaTemplate<String, PostViewedEvent> kafkaTemplateForPostViewedEvent;
    private final KafkaTemplate<String, LikeEvent> kafkaTemplateForLikedEvent;

    public KafkaProducer(KafkaTemplate<String, PostEvent> kafkaTemplate, KafkaTemplate<String, PostViewedEvent> kafkaTemplateForPostViewedEvent, KafkaTemplate<String, LikeEvent> kafkaTemplateForLikedEvent) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaTemplateForPostViewedEvent = kafkaTemplateForPostViewedEvent;
        this.kafkaTemplateForLikedEvent = kafkaTemplateForLikedEvent;
    }

    public void sendPostCreatedEvent(PostEvent event) {
        kafkaTemplate.executeInTransaction(operations -> {
            operations.send("post-created-topic", event);
            return true;
        });
    }

    public void sendPostDeletedEvent(PostEvent event) {
        kafkaTemplate.executeInTransaction(operations -> {
            operations.send("post-deleted-topic", event);
            return true;
        });
    }

    public void sendLikedEvent(LikeEvent event) {
        kafkaTemplateForLikedEvent.executeInTransaction(operations -> {
            operations.send("like-events-topic", event);
            return true;
        });
    }

    public void sendPostViewedEvent(PostViewedEvent event) {
        kafkaTemplateForPostViewedEvent.executeInTransaction(operations -> {
            operations.send("post-viewed-topic", event);
            return true;
        });
    }
}
