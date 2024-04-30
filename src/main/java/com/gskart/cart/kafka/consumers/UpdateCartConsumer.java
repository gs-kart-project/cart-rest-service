package com.gskart.cart.kafka.consumers;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class UpdateCartConsumer {

    @KafkaListener
    public void consume() {

    }
}
