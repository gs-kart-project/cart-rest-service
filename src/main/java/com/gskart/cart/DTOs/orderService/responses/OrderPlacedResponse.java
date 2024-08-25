package com.gskart.cart.DTOs.orderService.responses;

import lombok.Data;

@Data
public class OrderPlacedResponse extends ErrorResponse {
    private String orderId;
    private String orderStatus;
}
