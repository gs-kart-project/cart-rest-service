package com.gskart.cart.data.entities;

import lombok.Data;

import java.util.List;

@Data
public class Contact {
    private String firstName;
    private String lastName;
    private List<String> contactEmailIds;
    private List<PhoneNumber> phoneNumbers;
    private List<Address> addresses;
    private Short id;
}
