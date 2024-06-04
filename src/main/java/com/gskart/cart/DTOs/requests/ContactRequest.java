package com.gskart.cart.DTOs.requests;

import com.gskart.cart.data.entities.Address;
import com.gskart.cart.data.entities.PhoneNumber;
import lombok.Data;

import java.util.List;

@Data
public class ContactRequest {
    private String firstName;
    private String lastName;
    private List<String> contactEmailIds;
    private List<PhoneNumber> phoneNumbers;
    private List<Address> addresses;
    private ContactType contactType;
    private Short id;
}
