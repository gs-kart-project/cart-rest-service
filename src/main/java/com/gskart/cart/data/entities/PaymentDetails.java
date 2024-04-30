package com.gskart.cart.data.entities;

import lombok.Data;

@Data
public class PaymentDetails {
    /**
     * Payment Id in payment service
     */
    private Integer paymentId;
    private PaymentStatus paymentStatus;
}
