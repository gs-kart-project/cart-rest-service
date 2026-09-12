package com.gskart.cart.data.entities;

import lombok.Data;

@Data
public class OrderDetails {
    private Integer orderId;
    private OrderStatus orderStatus;
}
