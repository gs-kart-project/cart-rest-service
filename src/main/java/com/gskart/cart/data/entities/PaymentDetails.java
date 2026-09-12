package com.gskart.cart.data.entities;

import lombok.Data;

@Data
public class PaymentDetails {
    private Integer paymentId;
    private PaymentStatus paymentStatus;
}
