package com.gskart.cart.redis.repositories;

import com.gskart.cart.redis.entities.Cart;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

@Qualifier("cartCacheRepository")
public interface CartRepository extends CrudRepository<Cart, String> {
    @Override
    Optional<Cart> findById(String id);

    List<Cart> findCartsByCartUsername(String cartUsername);

    @Override
    <S extends Cart> S save(S entity);
}
