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

/**
 * Places the order for a checked-out cart by calling order-service, then writes the outcome back onto
 * the cart. Kafka-free (the {@code OrderPlaceListener} adapter feeds it). Triggered by an
 * {@code order.place} event.
 *
 * <p>Runs on a background thread with no request-scoped user, so it passes {@code modifiedBy}
 * explicitly (the cart owner, carried on {@link OrderRequest#getPlacedBy()}).
 *
 * <p>A failed order-service call is caught and recorded as {@link OrderStatus#COULD_NOT_PLACE_ORDER}
 * on the cart rather than thrown, so the listener does not propagate and trigger an indefinite Kafka
 * retry storm. Idempotent: if the cart is already {@code ORDER_PLACED}, an at-least-once redelivery is
 * skipped instead of placing a second order. (Circuit-breaker/timeout tuning is left for a later
 * change.)
 */
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
