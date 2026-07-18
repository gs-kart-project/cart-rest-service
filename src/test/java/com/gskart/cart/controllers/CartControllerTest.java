package com.gskart.cart.controllers;

import com.gskart.cart.DTOs.requests.CartRequest;
import com.gskart.cart.DTOs.requests.ContactRequest;
import com.gskart.cart.DTOs.requests.ContactType;
import com.gskart.cart.DTOs.response.CartResponse;
import com.gskart.cart.data.entities.ProductItem;
import com.gskart.cart.data.entities.QuantityUnit;
import com.gskart.cart.exceptions.CartNotFoundException;
import com.gskart.cart.exceptions.DeleteCartException;
import com.gskart.cart.exceptions.UpdateCartException;
import com.gskart.cart.mappers.CartMapper;
import com.gskart.cart.redis.entities.Cart;
import com.gskart.cart.services.ICartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Standalone MockMvc around just the controller — no Spring context, no security filter chain.
 * {@code @WebMvcTest} was tried first, but the slice doesn't scan {@code SecurityConfig} (a plain
 * {@code @Configuration}, not a web-layer stereotype), so Boot's OAuth2 resource-server
 * autoconfiguration falls back to its own default chain and fails wiring an {@code HttpSecurity}
 * bean that only {@code @EnableWebSecurity} would supply. The real chain is exercised instead by
 * the Testcontainers-backed {@code CartIntegrationTest}. Mirrors product-rest-service's
 * {@code ProductsControllerTest}/{@code CategoryControllerTest} pattern.
 */
@ExtendWith(MockitoExtension.class)
class CartControllerTest {

    @Mock
    private ICartService cartService;
    @Mock
    private CartMapper cartMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CartController(cartService, cartMapper)).build();
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ProductItem productItem() {
        ProductItem productItem = new ProductItem();
        productItem.setProductId(1);
        productItem.setProductName("Widget");
        productItem.setQuantity(1f);
        productItem.setUnitPrice(10.0);
        productItem.setTotalPrice(10.0);
        productItem.setQuantityUnit(QuantityUnit.COUNT);
        return productItem;
    }

    @Test
    void addCart_returns200_withMappedResponse() throws Exception {
        CartRequest request = new CartRequest();
        request.setProductItems(List.of(productItem()));
        Cart cart = new Cart();
        cart.setId("cart-1");
        Cart savedCart = new Cart();
        savedCart.setId("cart-1");
        CartResponse response = new CartResponse();
        response.setCartId("cart-1");

        when(cartMapper.cartRequestToCart(any(CartRequest.class))).thenReturn(cart);
        when(cartService.addNewCart(cart)).thenReturn(savedCart);
        when(cartMapper.cartToCartResponse(savedCart)).thenReturn(response);

        mockMvc.perform(post("/api/v1/carts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartId").value("cart-1"));
    }

    @Test
    void addCart_returns400_whenProductItemsMissing() throws Exception {
        CartRequest request = new CartRequest();
        request.setProductItems(List.of());

        mockMvc.perform(post("/api/v1/carts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cartService);
    }

    @Test
    void addCart_returns400_whenProductItemsFieldAbsent() throws Exception {
        mockMvc.perform(post("/api/v1/carts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cartService);
    }

    @Test
    void getCart_returns200_whenFound() throws Exception {
        Cart cart = new Cart();
        cart.setId("cart-1");
        CartResponse response = new CartResponse();
        response.setCartId("cart-1");
        when(cartService.getCartById("cart-1")).thenReturn(cart);
        when(cartMapper.cartToCartResponse(cart)).thenReturn(response);

        mockMvc.perform(get("/api/v1/carts/cart-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartId").value("cart-1"));
    }

    @Test
    void getCart_returns400_whenNotFound() throws Exception {
        when(cartService.getCartById("missing")).thenThrow(new CartNotFoundException("not found"));

        mockMvc.perform(get("/api/v1/carts/missing"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getActiveCart_returns200_whenPresent() throws Exception {
        Cart cart = new Cart();
        cart.setId("cart-1");
        CartResponse response = new CartResponse();
        response.setCartId("cart-1");
        when(cartService.getOpenCartForCurrentUser()).thenReturn(cart);
        when(cartMapper.cartToCartResponse(cart)).thenReturn(response);

        mockMvc.perform(get("/api/v1/carts/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartId").value("cart-1"));
    }

    @Test
    void getActiveCart_returns204_whenNoneOpen() throws Exception {
        when(cartService.getOpenCartForCurrentUser()).thenReturn(null);

        mockMvc.perform(get("/api/v1/carts/active"))
                .andExpect(status().isNoContent());
    }

    @Test
    void getActiveCart_returns204_whenCartNotFoundException() throws Exception {
        when(cartService.getOpenCartForCurrentUser()).thenThrow(new CartNotFoundException("none"));

        mockMvc.perform(get("/api/v1/carts/active"))
                .andExpect(status().isNoContent());
    }

    @Test
    void updateProductsInCart_returns200_onSuccess() throws Exception {
        when(cartService.updateProductsInCart(eq("cart-1"), anyList())).thenReturn(true);

        mockMvc.perform(put("/api/v1/carts/cart-1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(productItem()))))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    void updateProductsInCart_returns400_whenCartNotFound() throws Exception {
        when(cartService.updateProductsInCart(eq("missing"), anyList()))
                .thenThrow(new CartNotFoundException("not found"));

        mockMvc.perform(put("/api/v1/carts/missing/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(productItem()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteProductsInCart_returns200_onSuccess() throws Exception {
        when(cartService.deleteProductsFromCart(eq("cart-1"), anyList())).thenReturn(true);

        mockMvc.perform(delete("/api/v1/carts/cart-1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(1))))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    void deleteProductsInCart_returns400_whenCartNotFound() throws Exception {
        when(cartService.deleteProductsFromCart(eq("missing"), anyList()))
                .thenThrow(new CartNotFoundException("not found"));

        mockMvc.perform(delete("/api/v1/carts/missing/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(1))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateContact_returns200_onSuccess() throws Exception {
        ContactRequest request = new ContactRequest();
        request.setId((short) 1);
        request.setDeliveryDetailId((short) 1);
        request.setContactType(ContactType.BILLING);
        when(cartMapper.contactRequestToContact(any(ContactRequest.class)))
                .thenReturn(new com.gskart.cart.data.entities.Contact());

        mockMvc.perform(put("/api/v1/carts/cart-1/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    void updateContact_returns400_whenUpdateCartException() throws Exception {
        ContactRequest request = new ContactRequest();
        request.setId((short) 1);
        request.setDeliveryDetailId((short) 1);
        request.setContactType(ContactType.BILLING);
        when(cartMapper.contactRequestToContact(any(ContactRequest.class)))
                .thenReturn(new com.gskart.cart.data.entities.Contact());
        doThrow(new UpdateCartException("bad"))
                .when(cartService).updateDeliveryContact(eq("cart-1"), any(), any(), any());

        mockMvc.perform(put("/api/v1/carts/cart-1/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteContact_returns200_onSuccess() throws Exception {
        mockMvc.perform(delete("/api/v1/carts/cart-1/contacts")
                        .param("deliveryDetailId", "1")
                        .param("contactId", "1")
                        .param("contactType", "BILLING"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    void deleteContact_returns400_whenDeleteCartException() throws Exception {
        doThrow(new DeleteCartException("bad"))
                .when(cartService).deleteContact(eq("cart-1"), any(), any(), any());

        mockMvc.perform(delete("/api/v1/carts/cart-1/contacts")
                        .param("deliveryDetailId", "1")
                        .param("contactId", "1")
                        .param("contactType", "BILLING"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkoutCart_returns200_onSuccess() throws Exception {
        when(cartService.checkout("cart-1")).thenReturn("cart-1");

        mockMvc.perform(put("/api/v1/carts/cart-1/checkout"))
                .andExpect(status().isOk())
                .andExpect(content().string("Cart cart-1 checked out successfully"));
    }

    @Test
    void checkoutCart_returns400_whenUpdateCartException() throws Exception {
        when(cartService.checkout("cart-1")).thenThrow(new UpdateCartException("cannot checkout"));

        mockMvc.perform(put("/api/v1/carts/cart-1/checkout"))
                .andExpect(status().isBadRequest());
    }
}
