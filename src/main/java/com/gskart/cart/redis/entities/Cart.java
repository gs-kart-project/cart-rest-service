package com.gskart.cart.redis.entities;

import com.gskart.cart.data.entities.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

import java.util.List;
// Good to have Cart tracker entity for tracking different actions on the cart.
@EqualsAndHashCode(callSuper = true)
@Data
@RedisHash(value = "carts")
public class Cart extends BaseEntity {
    List<ProductItem> productItems;
    @Indexed
    String cartUsername;
    // Address
    CartStatus status;
    PaymentDetails paymentDetails;
    OrderDetails orderDetails;
    List<DeliveryDetails> deliveryDetails;

    //Integer ttl;

    /*@TimeToLive
    public long getTimeToLive() {
        return ttl * 60;
    }*/
}
