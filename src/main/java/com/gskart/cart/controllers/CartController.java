package com.gskart.cart.controllers;

import com.gskart.cart.DTOs.requests.CartRequest;
import com.gskart.cart.DTOs.requests.ContactRequest;
import com.gskart.cart.DTOs.requests.ContactType;
import com.gskart.cart.DTOs.response.CartResponse;
import com.gskart.cart.data.entities.Contact;
import com.gskart.cart.data.entities.ProductItem;
import com.gskart.cart.exceptions.CartNotFoundException;
import com.gskart.cart.exceptions.UpdateCartException;
import com.gskart.cart.mappers.CartMapper;
import com.gskart.cart.redis.entities.Cart;
import com.gskart.cart.security.models.GSKartResourceServerUserContext;
import com.gskart.cart.services.CartService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/carts")
public class CartController {
    private final CartService cartService;
    private final CartMapper cartMapper;
    private final GSKartResourceServerUserContext resourceServerUserContext;

    public CartController(CartService cartService, CartMapper cartMapper, GSKartResourceServerUserContext resourceServerUserContext) {
        this.cartService = cartService;
        this.cartMapper = cartMapper;
        this.resourceServerUserContext = resourceServerUserContext;
    }

    @PostMapping("")
    public ResponseEntity<CartResponse> addCart(@RequestBody CartRequest cartRequest) {
        if(cartRequest == null || cartRequest.getProductItems() == null || cartRequest.getProductItems().isEmpty()) {
            System.out.println("Cart request is not as expected.");
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        Cart cart = cartMapper.cartRequestToCart(cartRequest);
        Cart savedCart = cartService.addNewCart(cart);
        return ResponseEntity.ok(cartMapper.cartToCartResponse(savedCart));
    }

    @GetMapping("/{cartId}")
    public ResponseEntity<CartResponse> getCart(@PathVariable String cartId) {
        try {
            Cart cart = cartService.getCartById(cartId);
            return ResponseEntity.ok(cartMapper.cartToCartResponse(cart));

        } catch (CartNotFoundException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/active")
    public ResponseEntity<CartResponse> getActiveCart() {
        Cart cart = null;
        try {
            cart = cartService.getOpenCartForUser(resourceServerUserContext.getGskartResourceServerUser().getUsername());
            if(cart == null) {
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
            }
            return ResponseEntity.ok(cartMapper.cartToCartResponse(cart));
        } catch (CartNotFoundException e) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }

    }

    @PutMapping("/{cartId}/Products")
    public ResponseEntity<Boolean> updateProductsInCart(@PathVariable String cartId, @RequestBody List<ProductItem> productItemList) {
        try {
            boolean productAdded = cartService.updateProductsInCart(cartId, productItemList);
            return ResponseEntity.ok(productAdded);
        } catch (CartNotFoundException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @PutMapping("/{cartId}/contacts")
    // Endpoint to add contact
    public ResponseEntity<Boolean> updateContact(@PathVariable String cartId, @RequestBody ContactRequest contactRequest){
        try {
            Contact contact = cartMapper.contactRequestToContact(contactRequest);
            cartService.updateDeliveryContact(cartId, contact, contactRequest.getContactType());
            return ResponseEntity.ok(true);
        } catch (CartNotFoundException | UpdateCartException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @DeleteMapping("/{cartId}/contacts/{contactId}")
    public ResponseEntity<Boolean> deleteContact(@PathVariable String cartId, @PathVariable Short contactId, @RequestParam ContactType contactType) {
        try{
            cartService.deleteContact(cartId, contactId, contactType);
            return ResponseEntity.ok(true);
        } catch (UpdateCartException | CartNotFoundException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    // Endpoint to check out

    //
}
