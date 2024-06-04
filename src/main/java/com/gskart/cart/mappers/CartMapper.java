package com.gskart.cart.mappers;

import com.gskart.cart.DTOs.requests.CartRequest;
import com.gskart.cart.DTOs.requests.ContactRequest;
import com.gskart.cart.DTOs.requests.ContactType;
import com.gskart.cart.DTOs.response.CartResponse;
import com.gskart.cart.data.entities.Contact;
import com.gskart.cart.redis.entities.Cart;
import org.springframework.stereotype.Component;

@Component
public class CartMapper {
    public Cart cartRequestToCart(CartRequest cartRequest) {
        Cart cart = new Cart();
        cart.setId(cartRequest.getCartId());
        cart.setProductItems(cartRequest.getProductItems());
        return cart;
    }

    public CartResponse cartToCartResponse(Cart cart) {
        CartResponse cartResponse = new CartResponse();
        cartResponse.setCartId(cart.getId());
        cartResponse.setProductItems(cart.getProductItems());
        cartResponse.setPaymentDetails(cart.getPaymentDetails());
        cartResponse.setStatus(cart.getStatus());
        cartResponse.setOrderDetails(cart.getOrderDetails());
        cartResponse.setDeliveryDetails(cart.getDeliveryDetails());
        return cartResponse;
    }

    public Cart cartDbToCartCacheEntity(com.gskart.cart.data.entities.Cart dbCart){
        Cart cart = new Cart();
        cart.setMongoObjectId(dbCart.getId());
        cart.setProductItems(dbCart.getProductItems());
        cart.setPaymentDetails(dbCart.getPaymentDetails());
        cart.setStatus(dbCart.getStatus());
        cart.setOrderDetails(dbCart.getOrderDetails());
        cart.setDeliveryDetails(dbCart.getDeliveryDetails());
        cart.setCartUsername(dbCart.getCartUsername());
        cart.setCreatedBy(dbCart.getCreatedBy());
        cart.setCreatedOn(dbCart.getCreatedOn());
        cart.setModifiedBy(dbCart.getModifiedBy());
        cart.setModifiedOn(dbCart.getModifiedOn());
        return cart;
    }

    public com.gskart.cart.data.entities.Cart cartCacheToDbEntity(Cart cart) {
        com.gskart.cart.data.entities.Cart cartDbEntity = new com.gskart.cart.data.entities.Cart();
        cartDbEntity.setId(cart.getMongoObjectId());
        cartDbEntity.setProductItems(cart.getProductItems());
        cartDbEntity.setPaymentDetails(cart.getPaymentDetails());
        cartDbEntity.setOrderDetails(cart.getOrderDetails());
        cartDbEntity.setDeliveryDetails(cart.getDeliveryDetails());
        cartDbEntity.setStatus(cart.getStatus());
        cartDbEntity.setCartUsername(cart.getCartUsername());
        cartDbEntity.setCreatedBy(cart.getCreatedBy());
        cartDbEntity.setCreatedOn(cart.getCreatedOn());
        cartDbEntity.setModifiedBy(cart.getModifiedBy());
        cartDbEntity.setModifiedOn(cart.getModifiedOn());
        return cartDbEntity;
    }

    public Contact contactRequestToContact(ContactRequest contactRequest) {
        Contact contact = new Contact();
        contact.setContactEmailIds(contactRequest.getContactEmailIds());
        contact.setAddresses(contactRequest.getAddresses());
        contact.setPhoneNumbers(contactRequest.getPhoneNumbers());
        contact.setFirstName(contactRequest.getFirstName());
        contact.setLastName(contactRequest.getLastName());
        contact.setId(contactRequest.getId());
        return contact;
    }
}
