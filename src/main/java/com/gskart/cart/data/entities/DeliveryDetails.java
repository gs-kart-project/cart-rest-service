package com.gskart.cart.data.entities;

import lombok.Data;

import java.util.List;

@Data
public class DeliveryDetails {
    private Short id;
    private List<Integer> productIds;
    private Contact billingContact;
    private Contact shippingContact;
    private List<Contact> secondaryContacts;
}
