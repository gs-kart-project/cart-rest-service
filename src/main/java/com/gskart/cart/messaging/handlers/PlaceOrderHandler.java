package com.gskart.cart.messaging.handlers;

import com.gskart.cart.DTOs.orderService.requests.OrderRequest;
import com.gskart.cart.DTOs.orderService.responses.OrderPlacedResponse;
import com.gskart.cart.data.entities.OrderDetails;
import com.gskart.cart.data.entities.OrderStatus;
import com.gskart.cart.exceptions.CartNotFoundException;
import com.gskart.cart.redis.entities.Cart;
import com.gskart.cart.services.CartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

// Calls order-service for a checked-out cart and writes the result back onto the cart. Runs on a
// background thread with no logged-in user, so we pass modifiedBy explicitly — it's the cart owner
// from OrderRequest.placedBy. If the call fails we just record COULD_NOT_PLACE_ORDER instead of
// throwing, so Kafka doesn't retry forever, and if the cart's already ORDER_PLACED we skip so a
// redelivery doesn't double-place.
@Slf4j
@Component
public class PlaceOrderHandler {

    private static final String PLACE_ORDER_ENDPOINT = "order/place";

    private final RestTemplate restTemplate;
    private final CartService cartService;

    @Value("${gskart.order-service.url}")
    private String orderServiceBaseUrl;

    public PlaceOrderHandler(RestTemplate restTemplate, CartService cartService) {
        this.restTemplate = restTemplate;
        this.cartService = cartService;
    }

    public void handle(OrderRequest orderRequest) {
        String cartId = orderRequest.getCartId();

        if (alreadyPlaced(cartId)) {
            log.info("Cart {} is already ORDER_PLACED; skipping redelivered order.place event.", cartId);
            return;
        }

        OrderDetails orderDetails = new OrderDetails();
        try {
            URI placeOrderUrl = UriComponentsBuilder.fromUriString(orderServiceBaseUrl)
                    .path(PLACE_ORDER_ENDPOINT)
                    .build().toUri();
            RequestEntity<OrderRequest> orderRequestEntity =
                    new RequestEntity<>(orderRequest, HttpMethod.POST, placeOrderUrl);
            ResponseEntity<OrderPlacedResponse> response =
                    restTemplate.exchange(orderRequestEntity, OrderPlacedResponse.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Order was not placed for cart {}. Status code: {}", cartId, response.getStatusCode());
                if (response.hasBody()) {
                    orderDetails.setOrderId(convertToInteger(response.getBody().getOrderId()));
                    orderDetails.setOrderStatus(OrderStatus.COULD_NOT_PLACE_ORDER);
                }
            } else {
                orderDetails.setOrderId(convertToInteger(response.getBody().getOrderId()));
                orderDetails.setOrderStatus(OrderStatus.ORDER_PLACED);
            }
        } catch (RestClientException e) {
            log.error("order-service call failed for cart {}; recording COULD_NOT_PLACE_ORDER.", cartId, e);
            orderDetails.setOrderStatus(OrderStatus.COULD_NOT_PLACE_ORDER);
        }

        try {
            cartService.updateOrderDetails(cartId, orderDetails, orderRequest.getPlacedBy());
        } catch (CartNotFoundException e) {
            log.error("Failed to update order details for cart {}.", cartId, e);
        }
    }

    private boolean alreadyPlaced(String cartId) {
        try {
            Cart cart = cartService.getCartForSystem(cartId);
            return cart.getOrderDetails() != null
                    && cart.getOrderDetails().getOrderStatus() == OrderStatus.ORDER_PLACED;
        } catch (CartNotFoundException e) {
            // Not in the cache to inspect — let the normal flow run and log if it too can't find it.
            return false;
        }
    }

    private Integer convertToInteger(String numberString) {
        if (numberString == null || numberString.isEmpty()) {
            return null;
        }
        if (numberString.matches("-?\\d+")) {
            return Integer.valueOf(numberString);
        }
        return null;
    }
}
