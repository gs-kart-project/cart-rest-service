package com.gskart.cart.DTOs.orderService.requests;

import lombok.Data;

@Data
public class PhoneNumberDto {
    String id;
    private String number;
    private String type;
    private String countryCode;
}
