package com.gskart.cart.kafka.consumers;

import com.gskart.cart.DTOs.orderService.requests.OrderRequest;
import com.gskart.cart.DTOs.orderService.responses.OrderPlacedResponse;
import com.gskart.cart.data.entities.OrderDetails;
import com.gskart.cart.data.entities.OrderStatus;
import com.gskart.cart.exceptions.CartNotFoundException;
import com.gskart.cart.mappers.CartMapper;
import com.gskart.cart.services.CartService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderConsumerTest {

    @Mock
    private CartMapper cartMapper;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private CartService cartService;

    private OrderConsumer orderConsumer;

    @BeforeEach
    void setUp() {
        orderConsumer = new OrderConsumer(cartMapper, restTemplate, cartService);
        ReflectionTestUtils.setField(orderConsumer, "orderServiceBaseUrl", "http://localhost:4014/");
    }

    private OrderRequest orderRequest() {
        OrderRequest orderRequest = new OrderRequest();
        orderRequest.setCartId("cart-1");
        orderRequest.setPlacedBy("placed-by-user");
        return orderRequest;
    }

    private ConsumerRecord<String, Object> recordWith(OrderRequest orderRequest) {
        return new ConsumerRecord<>("order.place", 0, 0, "cart-1", orderRequest);
    }

    @Test
    void consumePlaceOrder_marksOrderPlaced_onSuccessfulResponse() throws CartNotFoundException {
        OrderRequest orderRequest = orderRequest();
        OrderPlacedResponse response = new OrderPlacedResponse();
        response.setOrderId("42");
        when(restTemplate.exchange(any(RequestEntity.class), eq(OrderPlacedResponse.class)))
                .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        orderConsumer.consumePlaceOrder(recordWith(orderRequest));

        ArgumentCaptor<OrderDetails> captor = ArgumentCaptor.forClass(OrderDetails.class);
        verify(cartService).updateOrderDetails(eq("cart-1"), captor.capture(), eq("placed-by-user"));
        assertThat(captor.getValue().getOrderId()).isEqualTo(42);
        assertThat(captor.getValue().getOrderStatus()).isEqualTo(OrderStatus.ORDER_PLACED);
    }

    @Test
    void consumePlaceOrder_marksCouldNotPlaceOrder_onErrorResponseWithBody() throws CartNotFoundException {
        OrderRequest orderRequest = orderRequest();
        OrderPlacedResponse response = new OrderPlacedResponse();
        response.setOrderId("7");
        when(restTemplate.exchange(any(RequestEntity.class), eq(OrderPlacedResponse.class)))
                .thenReturn(new ResponseEntity<>(response, HttpStatus.BAD_REQUEST));

        orderConsumer.consumePlaceOrder(recordWith(orderRequest));

        ArgumentCaptor<OrderDetails> captor = ArgumentCaptor.forClass(OrderDetails.class);
        verify(cartService).updateOrderDetails(eq("cart-1"), captor.capture(), eq("placed-by-user"));
        assertThat(captor.getValue().getOrderId()).isEqualTo(7);
        assertThat(captor.getValue().getOrderStatus()).isEqualTo(OrderStatus.COULD_NOT_PLACE_ORDER);
    }

    @Test
    void consumePlaceOrder_setsEmptyOrderDetails_onErrorResponseWithNoBody() throws CartNotFoundException {
        OrderRequest orderRequest = orderRequest();
        when(restTemplate.exchange(any(RequestEntity.class), eq(OrderPlacedResponse.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));

        orderConsumer.consumePlaceOrder(recordWith(orderRequest));

        ArgumentCaptor<OrderDetails> captor = ArgumentCaptor.forClass(OrderDetails.class);
        verify(cartService).updateOrderDetails(eq("cart-1"), captor.capture(), eq("placed-by-user"));
        assertThat(captor.getValue().getOrderId()).isNull();
        assertThat(captor.getValue().getOrderStatus()).isNull();
    }

    @Test
    void consumePlaceOrder_leavesOrderIdNull_whenBodyOrderIdIsBlank() throws CartNotFoundException {
        OrderRequest orderRequest = orderRequest();
        OrderPlacedResponse response = new OrderPlacedResponse();
        response.setOrderId("");
        when(restTemplate.exchange(any(RequestEntity.class), eq(OrderPlacedResponse.class)))
                .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        orderConsumer.consumePlaceOrder(recordWith(orderRequest));

        ArgumentCaptor<OrderDetails> captor = ArgumentCaptor.forClass(OrderDetails.class);
        verify(cartService).updateOrderDetails(eq("cart-1"), captor.capture(), eq("placed-by-user"));
        assertThat(captor.getValue().getOrderId()).isNull();
    }

    @Test
    void consumePlaceOrder_leavesOrderIdNull_whenBodyOrderIdIsNotNumeric() throws CartNotFoundException {
        OrderRequest orderRequest = orderRequest();
        OrderPlacedResponse response = new OrderPlacedResponse();
        response.setOrderId("not-a-number");
        when(restTemplate.exchange(any(RequestEntity.class), eq(OrderPlacedResponse.class)))
                .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        orderConsumer.consumePlaceOrder(recordWith(orderRequest));

        ArgumentCaptor<OrderDetails> captor = ArgumentCaptor.forClass(OrderDetails.class);
        verify(cartService).updateOrderDetails(eq("cart-1"), captor.capture(), eq("placed-by-user"));
        assertThat(captor.getValue().getOrderId()).isNull();
    }

    @Test
    void consumePlaceOrder_logsAndSwallows_whenCartNotFoundDuringUpdate() throws CartNotFoundException {
        OrderRequest orderRequest = orderRequest();
        OrderPlacedResponse response = new OrderPlacedResponse();
        response.setOrderId("42");
        when(restTemplate.exchange(any(RequestEntity.class), eq(OrderPlacedResponse.class)))
                .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));
        doThrow(new CartNotFoundException("missing")).when(cartService).updateOrderDetails(eq("cart-1"), any(), any());

        orderConsumer.consumePlaceOrder(recordWith(orderRequest));

        verify(cartService).updateOrderDetails(eq("cart-1"), any(), eq("placed-by-user"));
    }
}
