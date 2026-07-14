package com.gskart.cart.kafka.consumers;

import com.gskart.cart.mappers.CartMapper;
import com.gskart.cart.redis.entities.Cart;
import com.gskart.cart.redis.repositories.CartRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateCartConsumerTest {

    @Mock
    private CartMapper cartMapper;
    @Mock
    private CartRepository cartCacheRepository;
    @Mock
    private com.gskart.cart.data.repositories.CartRepository cartRepository;

    private UpdateCartConsumer updateCartConsumer;

    @BeforeEach
    void setUp() {
        updateCartConsumer = new UpdateCartConsumer(cartMapper, cartCacheRepository, cartRepository);
    }

    private ConsumerRecord<String, Object> recordWith(Cart cart) {
        return new ConsumerRecord<>("cart.update", 0, 0, cart.getId(), cart);
    }

    @Test
    void consume_savesToMongoAndBacksMongoIdIntoRedis_whenCartHasNoMongoIdYet() {
        Cart cachedCart = new Cart();
        cachedCart.setId("cart-1");
        cachedCart.setMongoObjectId(null);

        com.gskart.cart.data.entities.Cart dbEntity = new com.gskart.cart.data.entities.Cart();
        dbEntity.setId(null);
        when(cartMapper.cartCacheToDbEntity(cachedCart)).thenReturn(dbEntity);

        com.gskart.cart.data.entities.Cart savedDbEntity = new com.gskart.cart.data.entities.Cart();
        savedDbEntity.setId("mongo-1");
        when(cartRepository.save(dbEntity)).thenReturn(savedDbEntity);

        updateCartConsumer.consume(recordWith(cachedCart));

        verify(cartRepository).save(dbEntity);
        verify(cartRepository, never()).update(any());
        verify(cartCacheRepository).save(cachedCart);
        org.assertj.core.api.Assertions.assertThat(cachedCart.getMongoObjectId()).isEqualTo("mongo-1");
    }

    @Test
    void consume_updatesExistingMongoDoc_whenCartAlreadyHasMongoId() {
        Cart cachedCart = new Cart();
        cachedCart.setId("cart-1");
        cachedCart.setMongoObjectId("mongo-1");

        com.gskart.cart.data.entities.Cart dbEntity = new com.gskart.cart.data.entities.Cart();
        dbEntity.setId("mongo-1");
        when(cartMapper.cartCacheToDbEntity(cachedCart)).thenReturn(dbEntity);

        updateCartConsumer.consume(recordWith(cachedCart));

        verify(cartRepository).update(dbEntity);
        verify(cartRepository, never()).save(any());
        verify(cartCacheRepository, never()).save(any());
    }

    @Test
    void consume_treatsEmptyMongoId_asNotYetPersisted() {
        Cart cachedCart = new Cart();
        cachedCart.setId("cart-1");

        com.gskart.cart.data.entities.Cart dbEntity = new com.gskart.cart.data.entities.Cart();
        dbEntity.setId("");
        when(cartMapper.cartCacheToDbEntity(cachedCart)).thenReturn(dbEntity);
        when(cartRepository.save(dbEntity)).thenReturn(dbEntity);

        updateCartConsumer.consume(recordWith(cachedCart));

        verify(cartRepository).save(dbEntity);
        verify(cartRepository, never()).update(any());
    }
}
