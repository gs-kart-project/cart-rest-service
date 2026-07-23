package com.gskart.cart.messaging.kafka;

import com.gskart.cart.DTOs.orderService.requests.OrderRequest;
import com.gskart.cart.kafka.constants.KafkaConstants;
import com.gskart.cart.messaging.handlers.PlaceOrderHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.stereotype.Component;

/**
 * Kafka inbound adapter for {@code order.place}: deserializes the record and delegates to the
 * broker-agnostic {@link PlaceOrderHandler}. See {@link CartUpdateListener} for the adapter rationale.
 */
@Slf4j
@Component
public class OrderPlaceListener {

    private final PlaceOrderHandler handler;

    public OrderPlaceListener(PlaceOrderHandler handler) {
        this.handler = handler;
    }

    @KafkaListener(id = "placeOrder",
            topicPartitions = {
                    @TopicPartition(topic = KafkaConstants.Topic.ORDER_PLACE, partitions = "0")
            })
    public void onOrderPlace(ConsumerRecord<String, Object> consumerRecord) {
        OrderRequest orderRequest = (OrderRequest) consumerRecord.value();
        handler.handle(orderRequest);
    }
}
