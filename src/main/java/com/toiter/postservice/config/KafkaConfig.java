package com.toiter.postservice.config;

import com.toiter.postservice.model.LikeEvent;
import com.toiter.postservice.model.PostCreatedEvent;
import com.toiter.postservice.model.PostViewedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
@EnableTransactionManagement
public class KafkaConfig {

    @Value("${SPRING_KAFKA_BOOTSTRAP_SERVERS}")
    private String bootstrapServers;

    private Map<String, Object> producerConfigs(String transactionalId) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, transactionalId);
        return config;
    }

    private <T> ProducerFactory<String, T> producerFactory(Class<T> clazz, String transactionalId) {
        return new DefaultKafkaProducerFactory<>(producerConfigs(transactionalId));
    }

    @Bean
    public ProducerFactory<String, PostViewedEvent> producerFactoryForPostViewedEvent() {
        return producerFactory(PostViewedEvent.class, "post-view-transaction");
    }

    @Bean
    public ProducerFactory<String, PostCreatedEvent> producerFactory() {
        return producerFactory(PostCreatedEvent.class, "post-transaction");
    }

    @Bean
    public ProducerFactory<String, LikeEvent> producerFactoryForLikedEvent() {
        return producerFactory(LikeEvent.class, "like-transaction");
    }

    private Map<String, Object> consumerConfigs(String groupId) {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return config;
    }

    private <T> ConsumerFactory<String, T> consumerFactory(Class<T> clazz, String groupId) {
        JsonDeserializer<T> deserializer = new JsonDeserializer<>(clazz);
        deserializer.addTrustedPackages("*");
        return new DefaultKafkaConsumerFactory<>(consumerConfigs(groupId), new StringDeserializer(), deserializer);
    }

    @Bean
    public ConsumerFactory<String, PostCreatedEvent> consumerFactory() {
        return consumerFactory(PostCreatedEvent.class, "post-service-group");
    }

    @Bean
    public ConsumerFactory<String, LikeEvent> consumerFactoryForLikedEvent() {
        return consumerFactory(LikeEvent.class, "like-service-group");
    }

    @Bean
    public ConsumerFactory<String, PostViewedEvent> consumerFactoryForPostViewedEvent() {
        return consumerFactory(PostViewedEvent.class, "view-service-group");
    }

    @Bean
    public KafkaTemplate<String, PostViewedEvent> kafkaTemplateForPostViewedEvent(ProducerFactory<String, PostViewedEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public KafkaTemplate<String, PostCreatedEvent> kafkaTemplate(ProducerFactory<String, PostCreatedEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public KafkaTemplate<String, LikeEvent> kafkaTemplateForLikedEvent(ProducerFactory<String, LikeEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PostCreatedEvent> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, PostCreatedEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, LikeEvent> kafkaListenerContainerFactoryForLikedEvent() {
        ConcurrentKafkaListenerContainerFactory<String, LikeEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactoryForLikedEvent());
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PostViewedEvent> kafkaListenerContainerFactoryForPostViewedEvent() {
        ConcurrentKafkaListenerContainerFactory<String, PostViewedEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactoryForPostViewedEvent());
        return factory;
    }
}