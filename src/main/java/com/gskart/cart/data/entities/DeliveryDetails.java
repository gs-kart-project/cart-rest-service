package com.gskart.cart.data.entities;

import lombok.Data;

import java.util.List;

@Data
public class DeliveryDetails {
    private List<Contact> contacts;
    private Address billingAddress;
    private Address shippingAddress;
}
