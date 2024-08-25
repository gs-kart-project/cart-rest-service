package com.gskart.cart.DTOs.orderService.requests;

import lombok.Data;

@Data
public class OrderedItemDto {
    String id;
    Integer productId;
    String productName;
    String description;
    Float quantity;
    Double unitPrice;
    Double totalPrice;
    String quantityUnit;
}
