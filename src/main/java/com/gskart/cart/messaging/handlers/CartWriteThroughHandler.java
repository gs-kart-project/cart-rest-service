package com.gskart.cart.messaging.handlers;

import com.gskart.cart.data.repositories.ICartRepository;
import com.gskart.cart.mappers.CartMapper;
import com.gskart.cart.redis.entities.Cart;
import com.gskart.cart.redis.repositories.CartRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

// Write-through from Redis to Mongo on a cart.update event — kept Kafka-free so it's testable
// without a broker. Safe to run twice: first time inserts into Mongo and backfills the id into
// Redis, later runs just update Mongo — that's what makes at-least-once redelivery harmless.
@Slf4j
@Component
public class CartWriteThroughHandler {

    private final CartMapper cartMapper;
    private final CartRepository cartCacheRepository;
    private final ICartRepository cartDbRepository;

    public CartWriteThroughHandler(CartMapper cartMapper,
                                   CartRepository cartCacheRepository,
                                   @Qualifier("cartDbRepository") ICartRepository cartDbRepository) {
        this.cartMapper = cartMapper;
        this.cartCacheRepository = cartCacheRepository;
        this.cartDbRepository = cartDbRepository;
    }

    public void handle(Cart cartCached) {
        com.gskart.cart.data.entities.Cart cartEntity = cartMapper.cartCacheToDbEntity(cartCached);

        // First write-through: insert into Mongo, then back-fill the generated id into the cache.
        if (cartEntity.getId() == null || cartEntity.getId().isEmpty()) {
            cartEntity = cartDbRepository.save(cartEntity);
            cartCached.setMongoObjectId(cartEntity.getId());
            cartCacheRepository.save(cartCached);
            return;
        }

        // Subsequent write-through: same values already in Redis, so only Mongo needs updating.
        cartDbRepository.update(cartEntity);
    }
}
