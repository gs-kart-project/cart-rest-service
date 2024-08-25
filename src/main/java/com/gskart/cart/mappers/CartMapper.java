package com.gskart.cart.mappers;

import com.gskart.cart.DTOs.orderService.requests.*;
import com.gskart.cart.DTOs.requests.CartRequest;
import com.gskart.cart.DTOs.requests.ContactRequest;
import com.gskart.cart.DTOs.requests.ContactType;
import com.gskart.cart.DTOs.response.CartResponse;
import com.gskart.cart.data.entities.Contact;
import com.gskart.cart.data.entities.DeliveryDetails;
import com.gskart.cart.redis.entities.Cart;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Component
public class CartMapper {
    public Cart cartRequestToCart(CartRequest cartRequest) {
        Cart cart = new Cart();
        cart.setId(cartRequest.getCartId());
        cart.setProductItems(cartRequest.getProductItems());
        return cart;
    }

    public CartResponse cartToCartResponse(Cart cart) {
        CartResponse cartResponse = new CartResponse();
        cartResponse.setCartId(cart.getId());
        cartResponse.setProductItems(cart.getProductItems());
        cartResponse.setPaymentDetails(cart.getPaymentDetails());
        cartResponse.setStatus(cart.getStatus());
        cartResponse.setOrderDetails(cart.getOrderDetails());
        cartResponse.setDeliveryDetails(cart.getDeliveryDetails());
        return cartResponse;
    }

    public Cart cartDbToCartCacheEntity(com.gskart.cart.data.entities.Cart dbCart) {
        Cart cart = new Cart();
        cart.setMongoObjectId(dbCart.getId());
        cart.setProductItems(dbCart.getProductItems());
        cart.setPaymentDetails(dbCart.getPaymentDetails());
        cart.setStatus(dbCart.getStatus());
        cart.setOrderDetails(dbCart.getOrderDetails());
        cart.setDeliveryDetails(dbCart.getDeliveryDetails());
        cart.setCartUsername(dbCart.getCartUsername());
        cart.setCreatedBy(dbCart.getCreatedBy());
        cart.setCreatedOn(dbCart.getCreatedOn());
        cart.setModifiedBy(dbCart.getModifiedBy());
        cart.setModifiedOn(dbCart.getModifiedOn());
        return cart;
    }

    public com.gskart.cart.data.entities.Cart cartCacheToDbEntity(Cart cart) {
        com.gskart.cart.data.entities.Cart cartDbEntity = new com.gskart.cart.data.entities.Cart();
        cartDbEntity.setId(cart.getMongoObjectId());
        cartDbEntity.setProductItems(cart.getProductItems());
        cartDbEntity.setPaymentDetails(cart.getPaymentDetails());
        cartDbEntity.setOrderDetails(cart.getOrderDetails());
        cartDbEntity.setDeliveryDetails(cart.getDeliveryDetails());
        cartDbEntity.setStatus(cart.getStatus());
        cartDbEntity.setCartUsername(cart.getCartUsername());
        cartDbEntity.setCreatedBy(cart.getCreatedBy());
        cartDbEntity.setCreatedOn(cart.getCreatedOn());
        cartDbEntity.setModifiedBy(cart.getModifiedBy());
        cartDbEntity.setModifiedOn(cart.getModifiedOn());
        return cartDbEntity;
    }

    public Contact contactRequestToContact(ContactRequest contactRequest) {
        Contact contact = new Contact();
        contact.setContactEmailIds(contactRequest.getContactEmailIds());
        contact.setAddresses(contactRequest.getAddresses());
        contact.setPhoneNumbers(contactRequest.getPhoneNumbers());
        contact.setFirstName(contactRequest.getFirstName());
        contact.setLastName(contactRequest.getLastName());
        contact.setId(contactRequest.getId());
        return contact;
    }

    public OrderRequest cartRedisEntityToOrderRequest(Cart cart) {
        OrderRequest orderRequest = new OrderRequest();
        orderRequest.setCartId(cart.getId());
        orderRequest.setPlacedBy(cart.getCartUsername());
        orderRequest.setPlacedOn(OffsetDateTime.now(ZoneOffset.UTC));
        if (cart.getProductItems() != null) {
            orderRequest.setOrderedItems(
                    cart.getProductItems().stream()
                            .map(
                                    productItem -> {
                                        OrderedItemDto orderedItemDto = new OrderedItemDto();
                                        orderedItemDto.setProductId(productItem.getProductId());
                                        orderedItemDto.setProductName(productItem.getProductName());
                                        orderedItemDto.setDescription(productItem.getDescription());
                                        orderedItemDto.setQuantity(productItem.getQuantity());
                                        orderedItemDto.setUnitPrice(productItem.getUnitPrice());
                                        orderedItemDto.setTotalPrice(productItem.getTotalPrice());
                                        orderedItemDto.setQuantityUnit(productItem.getQuantityUnit().name());
                                        return orderedItemDto;
                                    }
                            )
                            .toList());
        }

        // Check and add payment details if required

        if (cart.getDeliveryDetails() != null) {
            List<DeliveryDetailDto> deliveryDetailDtos =
                    cart.getDeliveryDetails().stream()
                            .map(deliveryDetails -> {
                                DeliveryDetailDto deliveryDetailDto = new DeliveryDetailDto();
                                deliveryDetailDto.setProductIds(deliveryDetails.getProductIds());
                                if (deliveryDetailDto.getContacts() == null) {
                                    deliveryDetailDto.setContacts(new ArrayList<>());
                                }

                                if (deliveryDetails.getBillingContact() != null) {
                                    ContactDto contactDto = contactEntityToOrderContactRequest(deliveryDetails.getBillingContact(), ContactDto.ContactType.BILLING);
                                    deliveryDetailDto.getContacts().add(contactDto);
                                }

                                if(deliveryDetails.getShippingContact() != null) {
                                    ContactDto contactDto = contactEntityToOrderContactRequest(deliveryDetails.getShippingContact(), ContactDto.ContactType.SHIPPING);
                                    deliveryDetailDto.getContacts().add(contactDto);
                                }

                                if(deliveryDetails.getSecondaryContacts() != null){
                                    deliveryDetailDto.getContacts().addAll(
                                            deliveryDetails.getSecondaryContacts().stream()
                                                    .map(secondaryContact -> contactEntityToOrderContactRequest(secondaryContact, ContactDto.ContactType.SECONDARY))
                                                    .toList()
                                    );
                                }
                                return deliveryDetailDto;
                            })
                            .toList();
            orderRequest.setDeliveryDetails(deliveryDetailDtos);
        }
        return orderRequest;
    }

    private ContactDto contactEntityToOrderContactRequest(Contact contact, ContactDto.ContactType contactType) {
        ContactDto contactDto = new ContactDto();
        contactDto.setType(contactType.name());
        contactDto.setId(contact.getId().toString());
        contactDto.setEmailIds(contact.getContactEmailIds());
        contactDto.setFirstName(contact.getFirstName());
        contactDto.setLastName(contact.getLastName());
        if (contact.getPhoneNumbers() != null) {
            List<PhoneNumberDto> phoneNumberDtos =
                    contact.getPhoneNumbers().stream()
                            .map(phoneNumber -> {
                                PhoneNumberDto phoneNumberDto = new PhoneNumberDto();
                                phoneNumberDto.setNumber(phoneNumber.getNumber());
                                phoneNumberDto.setType(phoneNumberDto.getType());
                                phoneNumberDto.setCountryCode(phoneNumber.getCountryCode());
                                return phoneNumberDto;
                            })
                            .toList();
            contactDto.setPhoneNumbers(phoneNumberDtos);
        }
        if (contact.getAddresses() != null) {
            List<AddressDto> addressDtos =
                    contact.getAddresses().stream()
                            .map(address -> {
                                AddressDto addressDto = new AddressDto();
                                addressDto.setDoorNumber(address.getDoorNumber());
                                addressDto.setStreet(address.getStreet());
                                addressDto.setLine1(address.getLine1());
                                addressDto.setLine2(address.getLine2());
                                addressDto.setCity(address.getCity());
                                addressDto.setState(address.getState());
                                addressDto.setZip(address.getZip());
                                addressDto.setCountry(address.getCountry());
                                return addressDto;
                            })
                            .toList();
            contactDto.setAddresses(addressDtos);
        }
        return contactDto;
    }
}
