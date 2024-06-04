package com.gskart.cart.kafka.consumers;

import com.gskart.cart.kafka.constants.KafkaConstants;
import com.gskart.cart.mappers.CartMapper;
import com.gskart.cart.redis.entities.Cart;
import com.gskart.cart.redis.repositories.CartRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.PartitionOffset;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.KafkaUtils;
import org.springframework.stereotype.Component;

@Component
public class UpdateCartConsumer {

    private final CartMapper cartMapper;

    private final CartRepository cartCacheRepository;

    private final com.gskart.cart.data.repositories.CartRepository cartRepository;

    public UpdateCartConsumer(CartMapper cartMapper,
                              CartRepository cartCacheRepository,
                              @Qualifier("cartDbRepository")
                              com.gskart.cart.data.repositories.CartRepository cartRepository) {
        this.cartMapper = cartMapper;
        this.cartCacheRepository = cartCacheRepository;
        this.cartRepository = cartRepository;
    }

    @KafkaListener(id = "updateCart",
        topicPartitions = {
            @TopicPartition(topic = KafkaConstants.Topic.CART_UPDATE,
            partitions = "0")
        })
    public void consume(ConsumerRecord<String, Object> consumerRecord) {
        // 1. Parse Cart redis entity
        Cart cartCached = (Cart) consumerRecord.value();
        String consumerGroupId = KafkaUtils.getConsumerGroupId();
        /*if(consumerRecord.headers() != null){
            if(consumerRecord.headers().headers(KafkaHeaders.GROUP_ID) != null){
                while(consumerRecord.headers().headers(KafkaHeaders.GROUP_ID).iterator().hasNext()){
                    consumerGroupId = consumerRecord.headers().headers(KafkaHeaders.GROUP_ID).iterator().next().toString();
                    System.out.printf("Consumer group Id: %s", consumerGroupId);
                }
            }
        }*/
        System.out.printf("Consumer group Id: %s\n", consumerGroupId);

        // 2. Mapping cart redis entity to cart mongo entity.
        com.gskart.cart.data.entities.Cart cartEntity = cartMapper.cartCacheToDbEntity(cartCached);

        // 3. If Mongo id is not present, save the cartEntity in db and update cartCached in redis.
        if(cartEntity.getId() == null || cartEntity.getId().isEmpty()){
            cartEntity = cartRepository.save(cartEntity);
            cartCached.setMongoObjectId(cartEntity.getId());
            cartCacheRepository.save(cartCached);
            return;
        }

        // 4. Continue to call update cart entity in mongo. These are same values as Redis cache, so no need to store in redis
        cartRepository.update(cartEntity);
        //ack.acknowledge();
    }
}
