package com.toiter.postservice.producer;

import com.toiter.postservice.model.PostCreatedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaProducer {
    private final KafkaTemplate<String, PostCreatedEvent> kafkaTemplate;

    public KafkaProducer(KafkaTemplate<String, PostCreatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendPostCreatedEvent(PostCreatedEvent event) {
        kafkaTemplate.executeInTransaction(operations -> {
            operations.send("post-created-topic", event);
            return true;
        });
    }

    public void sendPostDeletedEvent(PostCreatedEvent event) {
        kafkaTemplate.executeInTransaction(operations -> {
            operations.send("post-deleted-topic", event);
            return true;
        });
    }
}
