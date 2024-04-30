package com.gskart.cart.data.repositories;

import com.gskart.cart.data.entities.Cart;
import com.gskart.cart.data.entities.CartStatus;

import java.util.List;
import java.util.Optional;

public interface ICartRepository {
    Cart save(Cart cart);

    Cart update(Cart cart);

    Optional<Cart> findById(String id);

    Optional<Cart> findByUsernameAndStatus(String username, CartStatus status);

    List<Cart> findAll();

    void deleteById(String id);
}
