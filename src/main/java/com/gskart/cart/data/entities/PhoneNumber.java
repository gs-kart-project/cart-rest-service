package com.gskart.cart.data.entities;

import lombok.Data;

@Data
public class PhoneNumber {
    private String number;
    private NumberType type;
    private String countryCode;
    public enum NumberType {
        MOBILE,
        PHONE
    }
}
