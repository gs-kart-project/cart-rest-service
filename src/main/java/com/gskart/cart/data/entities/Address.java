package com.gskart.cart.data.entities;

import lombok.Data;

@Data
public class Address {
    private String doorNumber;
    private String street;
    private String line1;
    private String line2;
    private String city;
    private String state;
    private String zip;
    private String country;
}
