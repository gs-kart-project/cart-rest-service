package com.gskart.cart.mappers;

import com.gskart.cart.DTOs.orderService.requests.OrderRequest;
import com.gskart.cart.DTOs.requests.CartRequest;
import com.gskart.cart.DTOs.requests.ContactRequest;
import com.gskart.cart.DTOs.response.CartResponse;
import com.gskart.cart.data.entities.*;
import com.gskart.cart.redis.entities.Cart;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CartMapperTest {

    private final CartMapper cartMapper = new CartMapper();

    private ProductItem productItem(int id) {
        ProductItem productItem = new ProductItem();
        productItem.setProductId(id);
        productItem.setProductName("Widget " + id);
        productItem.setDescription("desc-" + id);
        productItem.setQuantity(2f);
        productItem.setUnitPrice(5.0);
        productItem.setTotalPrice(10.0);
        productItem.setQuantityUnit(QuantityUnit.COUNT);
        return productItem;
    }

    @Test
    void cartRequestToCart_mapsIdAndProductItems() {
        CartRequest request = new CartRequest();
        request.setCartId("cart-1");
        request.setProductItems(List.of(productItem(1)));

        Cart cart = cartMapper.cartRequestToCart(request);

        assertThat(cart.getId()).isEqualTo("cart-1");
        assertThat(cart.getProductItems()).hasSize(1);
        assertThat(cart.getProductItems().get(0).getProductId()).isEqualTo(1);
    }

    @Test
    void cartToCartResponse_mapsAllFields() {
        Cart cart = new Cart();
        cart.setId("cart-1");
        cart.setProductItems(List.of(productItem(1)));
        cart.setStatus(CartStatus.CREATED);
        PaymentDetails paymentDetails = new PaymentDetails();
        paymentDetails.setPaymentId(1);
        cart.setPaymentDetails(paymentDetails);
        OrderDetails orderDetails = new OrderDetails();
        orderDetails.setOrderId(2);
        cart.setOrderDetails(orderDetails);
        DeliveryDetails deliveryDetails = new DeliveryDetails();
        deliveryDetails.setId((short) 1);
        cart.setDeliveryDetails(List.of(deliveryDetails));

        CartResponse response = cartMapper.cartToCartResponse(cart);

        assertThat(response.getCartId()).isEqualTo("cart-1");
        assertThat(response.getProductItems()).hasSize(1);
        assertThat(response.getPaymentDetails()).isEqualTo(paymentDetails);
        assertThat(response.getStatus()).isEqualTo(CartStatus.CREATED);
        assertThat(response.getOrderDetails()).isEqualTo(orderDetails);
        assertThat(response.getDeliveryDetails()).containsExactly(deliveryDetails);
    }

    @Test
    void cartDbToCartCacheEntity_mapsMongoIdIntoCacheEntity() {
        com.gskart.cart.data.entities.Cart dbCart = new com.gskart.cart.data.entities.Cart();
        dbCart.setId("mongo-id-1");
        dbCart.setProductItems(List.of(productItem(1)));
        dbCart.setStatus(CartStatus.APPENDED);
        dbCart.setCartUsername("SystemUser");
        dbCart.setCreatedBy("SystemUser");

        Cart cacheCart = cartMapper.cartDbToCartCacheEntity(dbCart);

        assertThat(cacheCart.getMongoObjectId()).isEqualTo("mongo-id-1");
        assertThat(cacheCart.getProductItems()).hasSize(1);
        assertThat(cacheCart.getStatus()).isEqualTo(CartStatus.APPENDED);
        assertThat(cacheCart.getCartUsername()).isEqualTo("SystemUser");
        assertThat(cacheCart.getCreatedBy()).isEqualTo("SystemUser");
    }

    @Test
    void cartCacheToDbEntity_mapsMongoObjectIdAsDbId() {
        Cart cacheCart = new Cart();
        cacheCart.setId("redis-id-1");
        cacheCart.setMongoObjectId("mongo-id-1");
        cacheCart.setProductItems(List.of(productItem(1)));
        cacheCart.setStatus(CartStatus.CREATED);
        cacheCart.setCartUsername("SystemUser");

        com.gskart.cart.data.entities.Cart dbCart = cartMapper.cartCacheToDbEntity(cacheCart);

        assertThat(dbCart.getId()).isEqualTo("mongo-id-1");
        assertThat(dbCart.getProductItems()).hasSize(1);
        assertThat(dbCart.getStatus()).isEqualTo(CartStatus.CREATED);
        assertThat(dbCart.getCartUsername()).isEqualTo("SystemUser");
    }

    @Test
    void contactRequestToContact_mapsAllFields() {
        ContactRequest request = new ContactRequest();
        request.setId((short) 3);
        request.setFirstName("Jane");
        request.setLastName("Doe");
        request.setContactEmailIds(List.of("jane@example.com"));
        PhoneNumber phoneNumber = new PhoneNumber();
        phoneNumber.setNumber("1234567890");
        request.setPhoneNumbers(List.of(phoneNumber));
        Address address = new Address();
        address.setCity("Bengaluru");
        request.setAddresses(List.of(address));

        Contact contact = cartMapper.contactRequestToContact(request);

        assertThat(contact.getId()).isEqualTo((short) 3);
        assertThat(contact.getFirstName()).isEqualTo("Jane");
        assertThat(contact.getLastName()).isEqualTo("Doe");
        assertThat(contact.getContactEmailIds()).containsExactly("jane@example.com");
        assertThat(contact.getPhoneNumbers()).containsExactly(phoneNumber);
        assertThat(contact.getAddresses()).containsExactly(address);
    }

    @Test
    void cartRedisEntityToOrderRequest_mapsProductsAndDeliveryDetailsWithAllContactTypes() {
        Cart cart = new Cart();
        cart.setId("cart-1");
        cart.setCartUsername("SystemUser");
        cart.setProductItems(List.of(productItem(1)));

        Contact billing = new Contact();
        billing.setId((short) 1);
        billing.setFirstName("Bill");
        Contact shipping = new Contact();
        shipping.setId((short) 2);
        shipping.setFirstName("Ship");
        Contact secondary = new Contact();
        secondary.setId((short) 3);
        secondary.setFirstName("Second");

        DeliveryDetails deliveryDetails = new DeliveryDetails();
        deliveryDetails.setProductIds(List.of(1));
        deliveryDetails.setBillingContact(billing);
        deliveryDetails.setShippingContact(shipping);
        deliveryDetails.setSecondaryContacts(List.of(secondary));
        cart.setDeliveryDetails(List.of(deliveryDetails));

        OrderRequest orderRequest = cartMapper.cartRedisEntityToOrderRequest(cart);

        assertThat(orderRequest.getCartId()).isEqualTo("cart-1");
        assertThat(orderRequest.getPlacedBy()).isEqualTo("SystemUser");
        assertThat(orderRequest.getPlacedOn()).isNotNull();
        assertThat(orderRequest.getOrderedItems()).hasSize(1);
        assertThat(orderRequest.getOrderedItems().get(0).getProductId()).isEqualTo(1);
        assertThat(orderRequest.getOrderedItems().get(0).getQuantityUnit()).isEqualTo("COUNT");

        assertThat(orderRequest.getDeliveryDetails()).hasSize(1);
        var contacts = orderRequest.getDeliveryDetails().get(0).getContacts();
        assertThat(contacts).hasSize(3);
        assertThat(contacts).extracting(c -> c.getType())
                .containsExactlyInAnyOrder("BILLING", "SHIPPING", "SECONDARY");
    }

    @Test
    void cartRedisEntityToOrderRequest_omitsBillingAndShippingContacts_whenNeitherPresent() {
        Cart cart = new Cart();
        cart.setId("cart-1");
        cart.setProductItems(List.of());

        DeliveryDetails deliveryDetails = new DeliveryDetails();
        Contact secondary = new Contact();
        secondary.setId((short) 9);
        deliveryDetails.setSecondaryContacts(List.of(secondary));
        cart.setDeliveryDetails(List.of(deliveryDetails));

        OrderRequest orderRequest = cartMapper.cartRedisEntityToOrderRequest(cart);

        var contacts = orderRequest.getDeliveryDetails().get(0).getContacts();
        assertThat(contacts).hasSize(1);
        assertThat(contacts.get(0).getType()).isEqualTo("SECONDARY");
    }

    @Test
    void cartRedisEntityToOrderRequest_handlesNullProductsAndDeliveryDetails() {
        Cart cart = new Cart();
        cart.setId("cart-1");
        cart.setCartUsername("SystemUser");

        OrderRequest orderRequest = cartMapper.cartRedisEntityToOrderRequest(cart);

        assertThat(orderRequest.getOrderedItems()).isNull();
        assertThat(orderRequest.getDeliveryDetails()).isNull();
    }

    @Test
    void cartRedisEntityToOrderRequest_mapsAddresses() {
        Cart cart = new Cart();
        cart.setId("cart-1");
        cart.setProductItems(List.of());

        Contact billing = new Contact();
        billing.setId((short) 1);
        Address address = new Address();
        address.setDoorNumber("12A");
        address.setStreet("Main St");
        address.setCity("Bengaluru");
        address.setCountry("India");
        billing.setAddresses(List.of(address));

        DeliveryDetails deliveryDetails = new DeliveryDetails();
        deliveryDetails.setBillingContact(billing);
        cart.setDeliveryDetails(List.of(deliveryDetails));

        OrderRequest orderRequest = cartMapper.cartRedisEntityToOrderRequest(cart);

        var addressDto = orderRequest.getDeliveryDetails().get(0).getContacts().get(0).getAddresses().get(0);
        assertThat(addressDto.getDoorNumber()).isEqualTo("12A");
        assertThat(addressDto.getStreet()).isEqualTo("Main St");
        assertThat(addressDto.getCity()).isEqualTo("Bengaluru");
        assertThat(addressDto.getCountry()).isEqualTo("India");
    }

    @Test
    void cartRedisEntityToOrderRequest_mapsPhoneNumberTypeFromSourceEnum() {
        Cart cart = new Cart();
        cart.setId("cart-1");
        cart.setProductItems(List.of());

        Contact billing = new Contact();
        billing.setId((short) 1);
        PhoneNumber phoneNumber = new PhoneNumber();
        phoneNumber.setNumber("1234567890");
        phoneNumber.setType(PhoneNumber.NumberType.MOBILE);
        billing.setPhoneNumbers(List.of(phoneNumber));

        DeliveryDetails deliveryDetails = new DeliveryDetails();
        deliveryDetails.setBillingContact(billing);
        cart.setDeliveryDetails(List.of(deliveryDetails));

        OrderRequest orderRequest = cartMapper.cartRedisEntityToOrderRequest(cart);

        var phoneNumberDto = orderRequest.getDeliveryDetails().get(0).getContacts().get(0).getPhoneNumbers().get(0);
        assertThat(phoneNumberDto.getNumber()).isEqualTo("1234567890");
        assertThat(phoneNumberDto.getType()).isEqualTo("MOBILE");
    }

    @Test
    void cartRedisEntityToOrderRequest_leavesPhoneNumberTypeNull_whenSourceTypeMissing() {
        Cart cart = new Cart();
        cart.setId("cart-1");
        cart.setProductItems(List.of());

        Contact billing = new Contact();
        billing.setId((short) 1);
        PhoneNumber phoneNumber = new PhoneNumber();
        phoneNumber.setNumber("1234567890");
        billing.setPhoneNumbers(List.of(phoneNumber));

        DeliveryDetails deliveryDetails = new DeliveryDetails();
        deliveryDetails.setBillingContact(billing);
        cart.setDeliveryDetails(List.of(deliveryDetails));

        OrderRequest orderRequest = cartMapper.cartRedisEntityToOrderRequest(cart);

        var phoneNumberDto = orderRequest.getDeliveryDetails().get(0).getContacts().get(0).getPhoneNumbers().get(0);
        assertThat(phoneNumberDto.getType()).isNull();
    }
}
