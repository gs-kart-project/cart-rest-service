package com.gskart.cart.messaging.kafka;

import com.gskart.cart.kafka.constants.KafkaConstants;
import com.gskart.cart.messaging.handlers.CartWriteThroughHandler;
import com.gskart.cart.redis.entities.Cart;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.stereotype.Component;

/**
 * Kafka inbound adapter for {@code cart.update}: deserializes the record and delegates to the
 * broker-agnostic {@link CartWriteThroughHandler}. Keeping {@code @KafkaListener} confined to the
 * adapter means swapping brokers later replaces this class, not the handler.
 */
@Slf4j
@Component
public class CartUpdateListener {

    private final CartWriteThroughHandler handler;

    public CartUpdateListener(CartWriteThroughHandler handler) {
        this.handler = handler;
    }

    @KafkaListener(id = "updateCart",
            topicPartitions = {
                    @TopicPartition(topic = KafkaConstants.Topic.CART_UPDATE, partitions = "0")
            })
    public void onCartUpdate(ConsumerRecord<String, Object> consumerRecord) {
        Cart cartCached = (Cart) consumerRecord.value();
        handler.handle(cartCached);
    }
}
