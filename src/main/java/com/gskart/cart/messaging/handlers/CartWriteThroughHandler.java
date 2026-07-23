package com.gskart.cart.messaging.handlers;

import com.gskart.cart.data.repositories.ICartRepository;
import com.gskart.cart.mappers.CartMapper;
import com.gskart.cart.redis.entities.Cart;
import com.gskart.cart.redis.repositories.CartRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Write-through of a cached cart into Mongo (the system of record), triggered by a {@code cart.update}
 * event. Kafka-free so it is unit-testable without a broker; the {@code CartUpdateListener} adapter
 * feeds it. Idempotent by construction: a first sighting inserts and back-fills the Mongo id into
 * Redis; subsequent sightings update the existing document, so an at-least-once redelivery from the
 * outbox relay is harmless.
 */
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
