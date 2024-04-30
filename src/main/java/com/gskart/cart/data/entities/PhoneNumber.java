package com.gskart.cart.data.entities;

public class PhoneNumber {
    private String number;
    private NumberType type;
    private String countryCode;
    public enum NumberType {
        MOBILE,
        PHONE
    }
}
