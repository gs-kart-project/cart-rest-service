package com.gskart.cart.DTOs.orderService.responses;

import lombok.Data;

@Data
public class ErrorResponse {
    private String status;
    private String message;

    public enum Status{
        ORDER_NOT_PLACED
    }
}
