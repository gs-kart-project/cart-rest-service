package com.gskart.cart.data.entities;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@Document("carts")
public class Cart extends BaseEntity {
    List<ProductItem> productItems;
    String cartUsername;
    CartStatus status;
    PaymentDetails paymentDetails;
    OrderDetails orderDetails;
    List<DeliveryDetails> deliveryDetails;
}
