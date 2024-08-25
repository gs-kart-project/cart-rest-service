package com.gskart.cart.DTOs.orderService.requests;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class OrderRequest {
    private String placedBy;
    private OffsetDateTime placedOn;
    private List<OrderedItemDto> orderedItems;
    private List<PaymentDetailDto> paymentDetails;
    private List<DeliveryDetailDto> deliveryDetails;
    private String cartId;
}


