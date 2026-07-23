package com.gskart.cart.messaging.handlers;

import com.gskart.cart.data.repositories.ICartRepository;
import com.gskart.cart.mappers.CartMapper;
import com.gskart.cart.redis.entities.Cart;
import com.gskart.cart.redis.repositories.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartWriteThroughHandlerTest {

    @Mock
    private CartMapper cartMapper;
    @Mock
    private CartRepository cartCacheRepository;
    @Mock
    private ICartRepository cartDbRepository;

    private CartWriteThroughHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CartWriteThroughHandler(cartMapper, cartCacheRepository, cartDbRepository);
    }

    @Test
    void handle_savesToMongoAndBacksMongoIdIntoRedis_whenCartHasNoMongoIdYet() {
        Cart cachedCart = new Cart();
        cachedCart.setId("cart-1");
        cachedCart.setMongoObjectId(null);

        com.gskart.cart.data.entities.Cart dbEntity = new com.gskart.cart.data.entities.Cart();
        dbEntity.setId(null);
        when(cartMapper.cartCacheToDbEntity(cachedCart)).thenReturn(dbEntity);

        com.gskart.cart.data.entities.Cart savedDbEntity = new com.gskart.cart.data.entities.Cart();
        savedDbEntity.setId("mongo-1");
        when(cartDbRepository.save(dbEntity)).thenReturn(savedDbEntity);

        handler.handle(cachedCart);

        verify(cartDbRepository).save(dbEntity);
        verify(cartDbRepository, never()).update(any());
        verify(cartCacheRepository).save(cachedCart);
        assertThat(cachedCart.getMongoObjectId()).isEqualTo("mongo-1");
    }

    @Test
    void handle_updatesExistingMongoDoc_whenCartAlreadyHasMongoId() {
        Cart cachedCart = new Cart();
        cachedCart.setId("cart-1");
        cachedCart.setMongoObjectId("mongo-1");

        com.gskart.cart.data.entities.Cart dbEntity = new com.gskart.cart.data.entities.Cart();
        dbEntity.setId("mongo-1");
        when(cartMapper.cartCacheToDbEntity(cachedCart)).thenReturn(dbEntity);

        handler.handle(cachedCart);

        verify(cartDbRepository).update(dbEntity);
        verify(cartDbRepository, never()).save(any());
        verify(cartCacheRepository, never()).save(any());
    }

    @Test
    void handle_treatsEmptyMongoId_asNotYetPersisted() {
        Cart cachedCart = new Cart();
        cachedCart.setId("cart-1");

        com.gskart.cart.data.entities.Cart dbEntity = new com.gskart.cart.data.entities.Cart();
        dbEntity.setId("");
        when(cartMapper.cartCacheToDbEntity(cachedCart)).thenReturn(dbEntity);
        when(cartDbRepository.save(dbEntity)).thenReturn(dbEntity);

        handler.handle(cachedCart);

        verify(cartDbRepository).save(dbEntity);
        verify(cartDbRepository, never()).update(any());
    }
}
