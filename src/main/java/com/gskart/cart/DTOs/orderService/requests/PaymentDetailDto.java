package com.gskart.cart.DTOs.orderService.requests;

import lombok.Data;

@Data
public class PaymentDetailDto {
    String id;
    private String paymentId;
    private ContactDto billContact;
    private String status;
}
