package com.gskart.cart.services;

import com.gskart.cart.data.entities.ProductItem;
import com.gskart.cart.exceptions.CartNotFoundException;
import com.gskart.cart.redis.entities.Cart;

import java.util.List;

public interface ICartService {
    Cart addNewCart(Cart cart);

    boolean addProductsToCart(String cartId, List<ProductItem> productItemList) throws CartNotFoundException;

    boolean updateProductsInCart(String cartId, List<ProductItem> productItemList) throws CartNotFoundException;

    boolean deleteProductsFromCart(String cartId, List<Integer> productIdList) throws CartNotFoundException;

    Cart getCartById(String cartId) throws CartNotFoundException;

    Cart getOpenCartForUser(String username) throws CartNotFoundException;
}
