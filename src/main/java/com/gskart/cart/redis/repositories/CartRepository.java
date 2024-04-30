package com.gskart.cart.redis.repositories;

import com.gskart.cart.data.entities.CartStatus;
import com.gskart.cart.redis.entities.Cart;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface CartRepository extends CrudRepository<Cart, String> {
    @Override
    Optional<Cart> findById(String id);

    Optional<Cart> findByCartUsernameAndStatusIsNot(String cartUsername, CartStatus status);

    @Override
    <S extends Cart> S save(S entity);
}
