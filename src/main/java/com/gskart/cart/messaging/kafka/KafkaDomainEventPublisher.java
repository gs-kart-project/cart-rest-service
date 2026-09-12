package com.gskart.cart.messaging.kafka;

import com.gskart.cart.messaging.DomainEvent;
import com.gskart.cart.messaging.DomainEventPublisher;
import com.gskart.cart.messaging.EventPublishException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// Kafka side of DomainEventPublisher. Publishes synchronously (blocks on the send future) so the
// outbox relay knows success/failure before acking the entry. Wire format is still driven by
// KafkaConfig's TYPE_MAPPINGS.
@Slf4j
@Component
public class KafkaDomainEventPublisher implements DomainEventPublisher {

    private static final long SEND_TIMEOUT_SECONDS = 30L;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaDomainEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(DomainEvent event) {
        ProducerRecord<String, Object> record =
                new ProducerRecord<>(event.getDestination(), event.getKey(), event.getPayload());
        try {
            kafkaTemplate.send(record).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("Published {} event (key {}) to {}.",
                    event.getEventType(), event.getKey(), event.getDestination());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventPublishException(
                    "Interrupted while publishing to " + event.getDestination(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new EventPublishException(
                    "Failed to publish " + event.getEventType() + " event to " + event.getDestination(), e);
        }
    }
}
