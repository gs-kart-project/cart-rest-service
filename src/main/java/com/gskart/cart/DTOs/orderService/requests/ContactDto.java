package com.gskart.cart.DTOs.orderService.requests;

import lombok.Data;

import java.util.List;

@Data
public class ContactDto {
    String id;
    private String firstName;
    private String lastName;
    private List<String> emailIds;
    List<PhoneNumberDto> phoneNumbers;
    List<AddressDto> addresses;
    private String type;

    public enum ContactType {
        BILLING,
        SHIPPING,
        SECONDARY
    }
}


