package com.gskart.cart.data.entities;

import lombok.Data;

@Data
public class OrderDetails {
    /**
     * Order Id in Order service
     */
    private Integer orderId;
    private OrderStatus orderStatus;
}
