package com.gskart.cart.DTOs.requests;

import com.gskart.cart.data.entities.ProductItem;
import lombok.Data;

import java.util.List;

@Data
public class CartRequest {
    String cartId;
    List<ProductItem> productItems;
}
