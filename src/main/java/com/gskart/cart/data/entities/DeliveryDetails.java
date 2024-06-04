package com.gskart.cart.data.entities;

import lombok.Data;

import java.util.List;

@Data
public class DeliveryDetails {
    private Contact billingContact;
    private Contact shippingContact;
    private List<Contact> secondaryContacts;
}
