package com.gskart.cart.messaging.handlers;

import com.gskart.cart.DTOs.orderService.requests.OrderRequest;
import com.gskart.cart.DTOs.orderService.responses.OrderPlacedResponse;
import com.gskart.cart.data.entities.OrderDetails;
import com.gskart.cart.data.entities.OrderStatus;
import com.gskart.cart.exceptions.CartNotFoundException;
import com.gskart.cart.redis.entities.Cart;
import com.gskart.cart.services.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaceOrderHandlerTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private CartService cartService;

    private PlaceOrderHandler handler;

    @BeforeEach
    void setUp() throws CartNotFoundException {
        handler = new PlaceOrderHandler(restTemplate, cartService);
        ReflectionTestUtils.setField(handler, "orderServiceBaseUrl", "http://localhost:4014/");
        // Default: cart present but not yet placed, so the idempotency guard lets the flow run.
        lenient().when(cartService.getCartForSystem(any())).thenReturn(new Cart());
    }

    private OrderRequest orderRequest() {
        OrderRequest orderRequest = new OrderRequest();
        orderRequest.setCartId("cart-1");
        orderRequest.setPlacedBy("placed-by-user");
        return orderRequest;
    }

    @Test
    void handle_marksOrderPlaced_onSuccessfulResponse() throws CartNotFoundException {
        OrderPlacedResponse response = new OrderPlacedResponse();
        response.setOrderId("42");
        when(restTemplate.exchange(any(RequestEntity.class), eq(OrderPlacedResponse.class)))
                .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        handler.handle(orderRequest());

        ArgumentCaptor<OrderDetails> captor = ArgumentCaptor.forClass(OrderDetails.class);
        verify(cartService).updateOrderDetails(eq("cart-1"), captor.capture(), eq("placed-by-user"));
        assertThat(captor.getValue().getOrderId()).isEqualTo(42);
        assertThat(captor.getValue().getOrderStatus()).isEqualTo(OrderStatus.ORDER_PLACED);
    }

    @Test
    void handle_marksCouldNotPlaceOrder_onErrorResponseWithBody() throws CartNotFoundException {
        OrderPlacedResponse response = new OrderPlacedResponse();
        response.setOrderId("7");
        when(restTemplate.exchange(any(RequestEntity.class), eq(OrderPlacedResponse.class)))
                .thenReturn(new ResponseEntity<>(response, HttpStatus.BAD_REQUEST));

        handler.handle(orderRequest());

        ArgumentCaptor<OrderDetails> captor = ArgumentCaptor.forClass(OrderDetails.class);
        verify(cartService).updateOrderDetails(eq("cart-1"), captor.capture(), eq("placed-by-user"));
        assertThat(captor.getValue().getOrderId()).isEqualTo(7);
        assertThat(captor.getValue().getOrderStatus()).isEqualTo(OrderStatus.COULD_NOT_PLACE_ORDER);
    }

    @Test
    void handle_recordsCouldNotPlaceOrder_whenOrderServiceCallThrows() throws CartNotFoundException {
        when(restTemplate.exchange(any(RequestEntity.class), eq(OrderPlacedResponse.class)))
                .thenThrow(new RestClientException("order-service down"));

        handler.handle(orderRequest());

        ArgumentCaptor<OrderDetails> captor = ArgumentCaptor.forClass(OrderDetails.class);
        verify(cartService).updateOrderDetails(eq("cart-1"), captor.capture(), eq("placed-by-user"));
        assertThat(captor.getValue().getOrderStatus()).isEqualTo(OrderStatus.COULD_NOT_PLACE_ORDER);
        assertThat(captor.getValue().getOrderId()).isNull();
    }

    @Test
    void handle_isIdempotent_skipsWhenCartAlreadyOrderPlaced() throws CartNotFoundException {
        Cart alreadyPlaced = new Cart();
        OrderDetails orderDetails = new OrderDetails();
        orderDetails.setOrderStatus(OrderStatus.ORDER_PLACED);
        alreadyPlaced.setOrderDetails(orderDetails);
        when(cartService.getCartForSystem("cart-1")).thenReturn(alreadyPlaced);

        handler.handle(orderRequest());

        verifyNoInteractions(restTemplate);
        verify(cartService, never()).updateOrderDetails(any(), any(), any());
    }

    @Test
    void handle_proceeds_whenCartNotFoundForIdempotencyCheck() throws CartNotFoundException {
        when(cartService.getCartForSystem("cart-1")).thenThrow(new CartNotFoundException("not cached"));
        OrderPlacedResponse response = new OrderPlacedResponse();
        response.setOrderId("42");
        when(restTemplate.exchange(any(RequestEntity.class), eq(OrderPlacedResponse.class)))
                .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        handler.handle(orderRequest());

        verify(cartService).updateOrderDetails(eq("cart-1"), any(), eq("placed-by-user"));
    }

    @Test
    void handle_leavesOrderIdNull_whenBodyOrderIdIsNotNumeric() throws CartNotFoundException {
        OrderPlacedResponse response = new OrderPlacedResponse();
        response.setOrderId("not-a-number");
        when(restTemplate.exchange(any(RequestEntity.class), eq(OrderPlacedResponse.class)))
                .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        handler.handle(orderRequest());

        ArgumentCaptor<OrderDetails> captor = ArgumentCaptor.forClass(OrderDetails.class);
        verify(cartService).updateOrderDetails(eq("cart-1"), captor.capture(), eq("placed-by-user"));
        assertThat(captor.getValue().getOrderId()).isNull();
    }

    @Test
    void handle_setsEmptyOrderDetails_onErrorResponseWithNoBody() throws CartNotFoundException {
        when(restTemplate.exchange(any(RequestEntity.class), eq(OrderPlacedResponse.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));

        handler.handle(orderRequest());

        ArgumentCaptor<OrderDetails> captor = ArgumentCaptor.forClass(OrderDetails.class);
        verify(cartService).updateOrderDetails(eq("cart-1"), captor.capture(), eq("placed-by-user"));
        assertThat(captor.getValue().getOrderId()).isNull();
        assertThat(captor.getValue().getOrderStatus()).isNull();
    }

    @Test
    void handle_logsAndSwallows_whenCartNotFoundDuringUpdate() throws CartNotFoundException {
        OrderPlacedResponse response = new OrderPlacedResponse();
        response.setOrderId("42");
        when(restTemplate.exchange(any(RequestEntity.class), eq(OrderPlacedResponse.class)))
                .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));
        doThrow(new CartNotFoundException("missing")).when(cartService).updateOrderDetails(eq("cart-1"), any(), any());

        handler.handle(orderRequest());

        verify(cartService).updateOrderDetails(eq("cart-1"), any(), eq("placed-by-user"));
    }
}
