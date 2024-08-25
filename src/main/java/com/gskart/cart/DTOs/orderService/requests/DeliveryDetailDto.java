package com.gskart.cart.DTOs.orderService.requests;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class DeliveryDetailDto {
    String id;
    private List<ContactDto> contacts;
    private OffsetDateTime deliveredOn;
    private OffsetDateTime shippedOn;
    private List<Integer> productIds;
    String status;
}
