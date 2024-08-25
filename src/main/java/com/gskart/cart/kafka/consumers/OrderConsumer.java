package com.gskart.cart.kafka.consumers;

import com.gskart.cart.DTOs.orderService.requests.OrderRequest;
import com.gskart.cart.DTOs.orderService.responses.OrderPlacedResponse;
import com.gskart.cart.data.entities.OrderDetails;
import com.gskart.cart.data.entities.OrderStatus;
import com.gskart.cart.exceptions.CartNotFoundException;
import com.gskart.cart.kafka.constants.KafkaConstants;
import com.gskart.cart.mappers.CartMapper;
import com.gskart.cart.services.CartService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Objects;

@Component
public class OrderConsumer {
    private final CartMapper cartMapper;
    private final RestTemplate restTemplate;
    private final CartService cartService;

    @Value("${gskart.order-service.url}")
    private String orderServiceBaseUrl;
    private static final String PlaceOrderEndpoint = "order/place";

    public OrderConsumer(CartMapper cartMapper, RestTemplate restTemplate, CartService cartService) {
        this.cartMapper = cartMapper;
        this.restTemplate = restTemplate;
        this.cartService = cartService;
    }

    // Todo Handle exception in OrderConsumer
    @KafkaListener(id = "placeOrder",
            topicPartitions = {
                    @TopicPartition(topic = KafkaConstants.Topic.ORDER_PLACE,
                            partitions = "0")
            })
    public void consumePlaceOrder(ConsumerRecord<String, Object> consumerRecord) {
        // 1. Get Order details from consumer record.
        OrderRequest orderRequest = (OrderRequest) consumerRecord.value();
        URI placeOrderUrl = UriComponentsBuilder
                .fromHttpUrl(orderServiceBaseUrl)
                .path(PlaceOrderEndpoint)
                .build().toUri();

        // 2. Call the order microservice to place order
        RequestEntity<OrderRequest> orderRequestEntity = new RequestEntity<>(orderRequest, HttpMethod.POST, placeOrderUrl);
        ResponseEntity<OrderPlacedResponse> orderPlacedResponseEntity = restTemplate.exchange(orderRequestEntity, OrderPlacedResponse.class);

        OrderDetails orderDetails = new OrderDetails();

        // 3. Write order status to Cart
        if (!orderPlacedResponseEntity.getStatusCode().is2xxSuccessful()) {
            System.out.printf(
                    "Order was not successfully placed for cart: %s. Status code: %s", orderRequest.getCartId(),
                    orderPlacedResponseEntity.getStatusCode());

            if (orderPlacedResponseEntity.hasBody()) {
                orderDetails.setOrderId(convertToInteger(orderPlacedResponseEntity.getBody().getOrderId()));
                orderDetails.setOrderStatus(OrderStatus.COULD_NOT_PLACE_ORDER);
            }
        } else {
            OrderPlacedResponse orderPlacedResponse = orderPlacedResponseEntity.getBody();
            orderDetails.setOrderId(convertToInteger(orderPlacedResponseEntity.getBody().getOrderId()));
            orderDetails.setOrderStatus(OrderStatus.ORDER_PLACED);
        }

        try {
            cartService.updateOrderDetails(orderRequest.getCartId(), orderDetails);
        } catch (CartNotFoundException e) {
            e.printStackTrace();
        }

    }

    private Integer convertToInteger(String numberString) {
        if (numberString == null || numberString.isEmpty()) {
            return null;
        }
        if (numberString.matches("-?\\d+")) {
            // Matches integers (including negative)
            return Integer.valueOf(numberString);
        } else {
            // Or return a default value
            return null;
        }
    }
}
