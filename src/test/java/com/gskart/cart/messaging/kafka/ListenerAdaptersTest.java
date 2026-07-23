package com.gskart.cart.messaging.kafka;

import com.gskart.cart.DTOs.orderService.requests.OrderRequest;
import com.gskart.cart.messaging.handlers.CartWriteThroughHandler;
import com.gskart.cart.messaging.handlers.PlaceOrderHandler;
import com.gskart.cart.redis.entities.Cart;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ListenerAdaptersTest {

    @Mock
    private CartWriteThroughHandler cartWriteThroughHandler;
    @Mock
    private PlaceOrderHandler placeOrderHandler;

    @Test
    void cartUpdateListener_delegatesDeserializedCartToHandler() {
        CartUpdateListener listener = new CartUpdateListener(cartWriteThroughHandler);
        Cart cart = new Cart();
        cart.setId("cart-1");

        listener.onCartUpdate(new ConsumerRecord<>("cart.update", 0, 0, "cart-1", cart));

        verify(cartWriteThroughHandler).handle(cart);
    }

    @Test
    void orderPlaceListener_delegatesDeserializedOrderRequestToHandler() {
        OrderPlaceListener listener = new OrderPlaceListener(placeOrderHandler);
        OrderRequest orderRequest = new OrderRequest();
        orderRequest.setCartId("cart-1");

        listener.onOrderPlace(new ConsumerRecord<>("order.place", 0, 0, "cart-1", orderRequest));

        verify(placeOrderHandler).handle(orderRequest);
    }
}
