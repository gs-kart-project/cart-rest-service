package com.gskart.cart.messaging.kafka;

import com.gskart.cart.messaging.DomainEvent;
import com.gskart.cart.messaging.EventPublishException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaDomainEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private KafkaDomainEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new KafkaDomainEventPublisher(kafkaTemplate);
    }

    private DomainEvent event(Object payload) {
        return DomainEvent.builder()
                .destination("cart.update")
                .key("cart-1")
                .eventType("CART_UPDATE")
                .payload(payload)
                .build();
    }

    @Test
    void publish_sendsRecordWithDestinationKeyAndPayload_onSuccess() {
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        Object payload = new Object();

        publisher.publish(event(payload));

        ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        assertThat(captor.getValue().topic()).isEqualTo("cart.update");
        assertThat(captor.getValue().key()).isEqualTo("cart-1");
        assertThat(captor.getValue().value()).isSameAs(payload);
    }

    @Test
    void publish_throwsEventPublishException_whenBrokerSendFails() {
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failed);

        assertThatThrownBy(() -> publisher.publish(event(new Object())))
                .isInstanceOf(EventPublishException.class);
    }
}
