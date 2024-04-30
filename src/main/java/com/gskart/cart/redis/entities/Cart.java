package com.gskart.cart.redis.entities;

import com.gskart.cart.data.entities.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.redis.core.RedisHash;

import java.util.List;
// Good to have Cart tracker entity for tracking different actions on the cart.
@EqualsAndHashCode(callSuper = true)
@Data
@RedisHash("carts")
public class Cart extends BaseEntity {
    List<ProductItem> productItems;
    String cartUsername;
    // Address
    CartStatus status;
    PaymentDetails paymentDetails;
    OrderDetails orderDetails;
    DeliveryDetails deliveryDetails;
}
