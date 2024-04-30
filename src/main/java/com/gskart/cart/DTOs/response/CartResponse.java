package com.gskart.cart.DTOs.response;

import com.gskart.cart.DTOs.requests.CartRequest;
import com.gskart.cart.data.entities.CartStatus;
import com.gskart.cart.data.entities.DeliveryDetails;
import com.gskart.cart.data.entities.OrderDetails;
import com.gskart.cart.data.entities.PaymentDetails;
import lombok.Data;

@Data
public class CartResponse extends CartRequest {
    CartStatus status;
    PaymentDetails paymentDetails;
    OrderDetails orderDetails;
    DeliveryDetails deliveryDetails;
}
