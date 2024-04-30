package com.gskart.cart.data.entities;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
// Good to have Cart tracker entity for tracking different actions on the cart.
@EqualsAndHashCode(callSuper = true)
@Data
@Document("carts")
public class Cart extends BaseEntity {
    List<ProductItem> productItems;
    String cartUsername;
    // Address
    CartStatus status;
    PaymentDetails paymentDetails;
    OrderDetails orderDetails;
    DeliveryDetails deliveryDetails;
}
