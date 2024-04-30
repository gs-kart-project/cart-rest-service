package com.gskart.cart.data.entities;

import lombok.Data;

@Data
public class ProductItem {
    Integer productId;
    String productName;
    String description;
    Float quantity;
    Double unitPrice;
    Double totalPrice;
    QuantityUnit quantityUnit;
}
