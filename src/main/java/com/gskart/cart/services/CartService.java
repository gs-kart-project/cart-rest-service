package com.gskart.cart.services;

import com.gskart.cart.DTOs.orderService.requests.OrderRequest;
import com.gskart.cart.DTOs.requests.ContactType;
import com.gskart.cart.data.entities.*;
import com.gskart.cart.data.repositories.ICartRepository;
import com.gskart.cart.exceptions.CartNotFoundException;
import com.gskart.cart.exceptions.DeleteCartException;
import com.gskart.cart.exceptions.UpdateCartException;
import com.gskart.cart.kafka.constants.KafkaConstants;
import com.gskart.cart.mappers.CartMapper;
import com.gskart.cart.redis.entities.Cart;
import com.gskart.cart.redis.repositories.CartRepository;
import com.gskart.cart.security.models.GSKartResourceServerUserContext;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.aggregation.BooleanOperators;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class CartService implements ICartService {
    private final CartRepository cartCacheRepository;

    private final ICartRepository cartDbRepository;

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final GSKartResourceServerUserContext resourceServerUserContext;
    private final CartMapper cartMapper;
    private final RedisTemplate<String, Object> stringObjectRedisTemplate;

   /* @Value("${gskart.redis.cart.ttl}")
    private Integer cartTTLMins;*/

    public CartService(
            CartRepository cartCacheRepository,
            @Qualifier("cartDbRepository") ICartRepository cartDbRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            GSKartResourceServerUserContext resourceServerUserContext,
            CartMapper cartMapper, RedisTemplate<String, Object> stringObjectRedisTemplate) {
        this.cartCacheRepository = cartCacheRepository;
        this.cartDbRepository = cartDbRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.resourceServerUserContext = resourceServerUserContext;
        this.cartMapper = cartMapper;
        this.stringObjectRedisTemplate = stringObjectRedisTemplate;
    }

    @Override
    public Cart addNewCart(Cart cart) {
        cart.setCreatedOn(OffsetDateTime.now(ZoneOffset.UTC));
        cart.setId(UUID.randomUUID().toString());
        cart.setStatus(CartStatus.CREATED);
        cart.setCartUsername(resourceServerUserContext.getGskartResourceServerUser().getUsername());
        cart.setCreatedBy(resourceServerUserContext.getGskartResourceServerUser().getUsername());
        // create delivery details
        if(cart.getDeliveryDetails() == null){
            DeliveryDetails deliveryDetails = new DeliveryDetails();
            deliveryDetails.setId((short) 1);
            deliveryDetails.setProductIds(cart.getProductItems().stream().map(ProductItem::getProductId).toList());
            cart.setDeliveryDetails(new ArrayList<>(){{add(deliveryDetails);}});
        }
//        cart.setTtl(cartTTLMins);
        Cart savedCart = cartCacheRepository.save(cart);
        saveCartInDb(savedCart);
        return savedCart;
    }

    @Override
    public boolean addProductsToCart(String cartId, List<ProductItem> productItemList) throws CartNotFoundException {
        Cart cart = getCartById(cartId);
        List<ProductItem> productItemListExisting = cart.getProductItems();
        productItemListExisting.addAll(productItemList);
        if(cart.getDeliveryDetails() != null){
            Optional<DeliveryDetails> deliveryDetailsOptional = cart.getDeliveryDetails().stream().filter(dd-> dd.getId().equals((short)1)).findFirst();
            if(deliveryDetailsOptional.isPresent()){
                DeliveryDetails deliveryDetails = deliveryDetailsOptional.get();
                deliveryDetails.getProductIds().addAll(cart.getProductItems().stream().map(ProductItem::getProductId).toList());
            }
        }
//        cart.setTtl(cartTTLMins);
        cartCacheRepository.save(cart);
        return true;
    }

    @Override
    public boolean updateProductsInCart(String cartId, List<ProductItem> productItemList) throws CartNotFoundException {
        Cart cart = getCartById(cartId);
        List<ProductItem> productItemListExisting = cart.getProductItems();
        for(ProductItem productItem : productItemList) {
            productItemListExisting.stream()
                    .filter(pri->pri.getProductId().equals(productItem.getProductId()))
                    .findFirst().ifPresent(productItemListExisting::remove);
            productItemListExisting.add(productItem);
        }
        if(cart.getDeliveryDetails() != null){
            Optional<DeliveryDetails> deliveryDetailsOptional = cart.getDeliveryDetails().stream().filter(dd-> dd.getId().equals((short)1)).findFirst();
            if(deliveryDetailsOptional.isPresent()){
                DeliveryDetails deliveryDetails = deliveryDetailsOptional.get();
                deliveryDetails.getProductIds().clear();
                deliveryDetails.getProductIds().addAll(cart.getProductItems().stream().map(ProductItem::getProductId).toList());
            }
        }
        cart.setStatus(CartStatus.APPENDED);
        cart.setModifiedBy(resourceServerUserContext.getGskartResourceServerUser().getUsername());
        cart.setModifiedOn(OffsetDateTime.now(ZoneOffset.UTC));
//        cart.setTtl(cartTTLMins);
        Cart savedCart = cartCacheRepository.save(cart);
        saveCartInDb(savedCart);
        return true;
    }

    @Override
    public boolean deleteProductsFromCart(String cartId, List<Integer> productIdList) throws CartNotFoundException {
        Cart cart = getCartById(cartId);
        List<ProductItem> productItemListExisting = cart.getProductItems();
        for(Integer productId : productIdList) {
            productItemListExisting.removeIf(prie -> prie.getProductId().equals(productId));
        }
        cart.setStatus(CartStatus.APPENDED);
        cart.setModifiedBy(resourceServerUserContext.getGskartResourceServerUser().getUsername());
        cart.setModifiedOn(OffsetDateTime.now(ZoneOffset.UTC));
        Cart savedCart = cartCacheRepository.save(cart);
        //        cart.setTtl(cartTTLMins);
        saveCartInDb(savedCart);
        return true;
    }

    @Override
    public Cart getCartById(String cartId) throws CartNotFoundException {
        Optional<Cart> cartOptional = cartCacheRepository.findById(cartId);
        if(cartOptional.isEmpty()){
            throw new CartNotFoundException(String.format("Cart with id:%s does not exist in Redis.", cartId));
        }
        return cartOptional.get();
    }

    @Override
    public Cart getOpenCartForUser(String username) throws CartNotFoundException {
        Cart cart = null;
        List<Cart> cartCacheList = cartCacheRepository.findCartsByCartUsername(username);
        if(cartCacheList!=null && !cartCacheList.isEmpty()){
            cart = cartCacheList.stream().filter(c->!c.getStatus().equals(CartStatus.CHECKED_OUT))
                    .findFirst().orElse(null);
        }

        // In case Cart does not exist in cart, the user will check in Db.
        if(cart != null){
            return cart;
        }

        System.out.printf("Cart not found for user %s in cache, looking up from Db.", username);
        Optional<com.gskart.cart.data.entities.Cart> cartDbEntityOptional = cartDbRepository.findByUsernameAndStatusIsNot(username, CartStatus.CHECKED_OUT);
        if(cartDbEntityOptional.isEmpty()){
            throw new CartNotFoundException(String.format("Cart doesn't exist for user: %s in both cache and database", username));
        }
        cart = cartMapper.cartDbToCartCacheEntity(cartDbEntityOptional.get());
        //cart.setTtl(cartTTLMins);
        cart = cartCacheRepository.save(cart);
        /*long cartTtlSeconds = cartTTLMins * 60*60;
        stringObjectRedisTemplate.expire(String.format("carts:%s:idx", cart.getId()), Duration.of(cartTTLMins, ChronoUnit.MINUTES));
        stringObjectRedisTemplate.expire(String.format("carts:cartUsername:%s", cart.getCartUsername()), Duration.of(cartTTLMins, ChronoUnit.MINUTES));*/
        return cart;
    }

    private void saveCartInDb(Cart cart){
        final ProducerRecord<String, Object> cartProducerRecord= new ProducerRecord<>(KafkaConstants.Topic.CART_UPDATE, cart.getId(), cart);
        CompletableFuture<SendResult<String, Object>> sendResultCompletableFuture = kafkaTemplate.send(cartProducerRecord);
        sendResultCompletableFuture.whenComplete((result, ex)->{
            if(ex!=null){
                System.out.printf("Cart (Id: %s) was not sent to Kafka topic %s. Exception details are below", cart.getId(), KafkaConstants.Topic.CART_UPDATE);
                ex.printStackTrace();
                return;
            }
            System.out.printf("Cart (Id: %s) sent to Kafka topic %s successfully.", cart.getId(), KafkaConstants.Topic.CART_UPDATE);
        });
    }

    @Override
    public boolean updateDeliveryContact(String cartId, Short deliveryDetailId, Contact contact, ContactType contactType) throws CartNotFoundException, UpdateCartException {
        Cart cart = getCartById(cartId);

        if(contact.getId() == null || contact.getId() == 0){
            throw new UpdateCartException(String.format("Cart id:%s. Cannot add secondary contact without Id.", cartId));
        }

        DeliveryDetails deliveryDetailsToUpdate = null;
        if(cart.getDeliveryDetails() == null){
            cart.setDeliveryDetails(new ArrayList<>());
            deliveryDetailsToUpdate = new DeliveryDetails();
            deliveryDetailsToUpdate.setId((short) 1);
            deliveryDetailsToUpdate.setSecondaryContacts(new ArrayList<>());
            cart.getDeliveryDetails().add(deliveryDetailsToUpdate);
        }

        if(!cart.getDeliveryDetails().isEmpty()){
            var deliveryDetailOptional = cart.getDeliveryDetails().stream().filter(deliveryDetails -> deliveryDetails.getId().equals(deliveryDetailId)).findFirst();
            if(deliveryDetailOptional.isPresent()){
                deliveryDetailsToUpdate = deliveryDetailOptional.get();
            }
            else {
                throw new UpdateCartException(String.format("Cart id:%s. Cannot ascertain the delivery details to update contact. Check the delivery Id", cartId));
            }
        }

        switch(contactType){
            case BILLING -> deliveryDetailsToUpdate.setBillingContact(contact);
            case SHIPPING -> deliveryDetailsToUpdate.setShippingContact(contact);
            case SECONDARY ->{
                if(deliveryDetailsToUpdate.getSecondaryContacts() == null){
                    deliveryDetailsToUpdate.setSecondaryContacts(new ArrayList<>());
                }
                Optional<Contact> secondaryContactOptional = deliveryDetailsToUpdate.getSecondaryContacts()
                        .stream()
                        .filter(existingContact -> Objects.equals(existingContact.getId(), contact.getId()))
                        .findFirst();
                if(secondaryContactOptional.isPresent()){
                    Contact secondaryContact = secondaryContactOptional.get();
                    secondaryContact.setFirstName(contact.getFirstName());
                    secondaryContact.setLastName(contact.getLastName());
                    secondaryContact.setContactEmailIds(contact.getContactEmailIds());
                    secondaryContact.setPhoneNumbers(contact.getPhoneNumbers());
                    secondaryContact.setAddresses(contact.getAddresses());
                }
                else {
                    deliveryDetailsToUpdate.getSecondaryContacts().add(contact);
                }
            }
        }
        cart.setStatus(CartStatus.APPENDED);
        cart.setModifiedBy(resourceServerUserContext.getGskartResourceServerUser().getUsername());
        cart.setModifiedOn(OffsetDateTime.now(ZoneOffset.UTC));

        cart = cartCacheRepository.save(cart);
        saveCartInDb(cart);

        return true;
    }

    @Override
    public boolean deleteContact(String cartId, Short deliveryDetailId, Short contactId, ContactType contactType) throws CartNotFoundException, UpdateCartException, DeleteCartException {
        Cart cart = getCartById(cartId);
        if(cart.getDeliveryDetails() == null){
            throw new DeleteCartException(String.format("Delivery details doesn't exist for cart: %s", cart.getId()));
        }
        DeliveryDetails deliveryDetailsToUpdate = cart.getDeliveryDetails().stream().filter(dd -> dd.getId().equals(deliveryDetailId)).findFirst().orElse(null);
        if(deliveryDetailsToUpdate == null){
            throw new DeleteCartException(String.format("Delivery details doesn't exist for cart: %s", cart.getId()));
        }

        switch(contactType){
            case BILLING -> deliveryDetailsToUpdate.setBillingContact(null);
            case SHIPPING -> deliveryDetailsToUpdate.setShippingContact(null);
            case SECONDARY -> {
               Optional<Contact> secondaryContactOptional = deliveryDetailsToUpdate.getSecondaryContacts().stream().filter(existingContact -> Objects.equals(existingContact.getId(), contactId)).findFirst();
               if(secondaryContactOptional.isPresent()){
                   deliveryDetailsToUpdate.getSecondaryContacts().remove(secondaryContactOptional.get());
               }
               else {
                   throw new UpdateCartException(String.format("Secondary contact with Id: %d does not exist in the Cart: %s.", contactId, cartId));
               }
            }
        }
        cart = cartCacheRepository.save(cart);
        cart.setStatus(CartStatus.APPENDED);
        cart.setModifiedBy(resourceServerUserContext.getGskartResourceServerUser().getUsername());
        cart.setModifiedOn(OffsetDateTime.now(ZoneOffset.UTC));
        saveCartInDb(cart);

        return true;
    }

    /*
    Todo Checkout flow
    checkout - Initiate payment
    Once payment is success ->
        1. Update payment id cart.
        2. Create a new order.
    If Payment fails
        1. Update payment status in Cart
    Once create new order is success
        1. Update cart with order details
     */
    public String checkout(String cartId) throws CartNotFoundException, UpdateCartException {
        Cart cart = getCartById(cartId);
        // At least 1 product should exist in the cart
        if(cart.getProductItems() == null || cart.getProductItems().isEmpty()){
            throw new UpdateCartException(String.format("Cart %s cannot be checked out as there are no products selected", cartId));
        }

        // Delivery details must be present
        if(cart.getDeliveryDetails() == null || cart.getDeliveryDetails().isEmpty()){
            throw new UpdateCartException(String.format("Cart %s cannot be checked out. Delivery details are not updated", cartId));
        }

        // Check if Billing and Shipping contacts have been updated
        StringBuilder billingContactsMissingIds = new StringBuilder();
        StringBuilder shippingContactsMissingIds = new StringBuilder();
        StringBuilder productIdsMissingIds = new StringBuilder();
        for(DeliveryDetails deliveryDetails : cart.getDeliveryDetails()){
            if(deliveryDetails.getBillingContact() == null){
               appendToStringWithComma(billingContactsMissingIds, deliveryDetails.getId().toString());
            }

            if(deliveryDetails.getShippingContact() == null){
               appendToStringWithComma(shippingContactsMissingIds, deliveryDetails.getId().toString());
            }

            if(deliveryDetails.getProductIds() == null){
                appendToStringWithComma(productIdsMissingIds, deliveryDetails.getId().toString());
            }
        }



        StringBuilder exceptionMessage = new StringBuilder();
        if(!billingContactsMissingIds.isEmpty()){
            exceptionMessage.append(String.format("Billing contacts missing in delivery details ids: %s", billingContactsMissingIds));
        }
        if(!shippingContactsMissingIds.isEmpty()){
            exceptionMessage.append(String.format("Shipping contacts missing in delivery details ids: %s", shippingContactsMissingIds));
        }
        if(!productIdsMissingIds.isEmpty()){
            exceptionMessage.append(String.format("Product ids missing in delivery details ids: %s", productIdsMissingIds));
        }

        if(!exceptionMessage.isEmpty()){
            exceptionMessage.insert(0, String.format("Cart %s cannot be checked out. ", cartId));
            throw new UpdateCartException(exceptionMessage.toString());
        }

        cart.setStatus(CartStatus.CHECKED_OUT);
        cart.setModifiedBy(resourceServerUserContext.getGskartResourceServerUser().getUsername());
        cart.setModifiedOn(OffsetDateTime.now(ZoneOffset.UTC));
        cartCacheRepository.save(cart);
        // Save cart in Db (Kafka)
        saveCartInDb(cart);

        // Place Order (Order service call via Kafka topic)
        placeOrder(cart);

        return cart.getId();
    }

    private void appendToStringWithComma(StringBuilder stringBuilder, String value){
        if(!stringBuilder.isEmpty()){
            stringBuilder.append(", ");
        }
        stringBuilder.append(value);
    }

    private void placeOrder(Cart cart){
        OrderRequest orderRequest = cartMapper.cartRedisEntityToOrderRequest(cart);
        final ProducerRecord<String, Object> placeOrderProducerRecord= new ProducerRecord<>(KafkaConstants.Topic.ORDER_PLACE, orderRequest.getCartId(), orderRequest);
        CompletableFuture<SendResult<String, Object>> placeOrderFuture = kafkaTemplate.send(placeOrderProducerRecord);
        placeOrderFuture.whenComplete((result, ex) -> {
            if(ex != null){
                System.out.printf("Couldn't place order for cart %s. Error occurred while sending Order Request to Topic %s", cart.getId(), KafkaConstants.Topic.ORDER_PLACE);
                ex.printStackTrace();
                OrderDetails orderDetails = new OrderDetails();
                orderDetails.setOrderStatus(OrderStatus.COULD_NOT_PLACE_ORDER);
                try {
                    updateOrderDetails(cart.getId(), orderDetails);
                } catch (CartNotFoundException e) {
                    e.printStackTrace();
                }
                return;
            }
            System.out.printf("Order successfully placed for cart: %s", cart.getId());
        });
    }

    public Cart updateOrderDetails(String cartId, OrderDetails orderDetails) throws CartNotFoundException {
        Cart cart = getCartById(cartId);
        if(cart.getOrderDetails() == null){
            cart.setOrderDetails(orderDetails);
        }
        else {
            cart.getOrderDetails().setOrderId(orderDetails.getOrderId());
            cart.getOrderDetails().setOrderStatus(orderDetails.getOrderStatus());
        }
        cart.setModifiedBy(resourceServerUserContext.getGskartResourceServerUser().getUsername());
        cart.setModifiedOn(OffsetDateTime.now(ZoneOffset.UTC));
        cartCacheRepository.save(cart);
        saveCartInDb(cart);
        return cart;
    }

    public Cart updatePaymentDetails(String cartId, PaymentDetails paymentDetails) throws CartNotFoundException {
        Cart cart = getCartById(cartId);
        if(cart.getPaymentDetails() == null){
            cart.setPaymentDetails(paymentDetails);
        }
        else{
            cart.getPaymentDetails().setPaymentId(paymentDetails.getPaymentId());
            cart.getPaymentDetails().setPaymentStatus(paymentDetails.getPaymentStatus());
        }

        cart.setModifiedBy(resourceServerUserContext.getGskartResourceServerUser().getUsername());
        cart.setModifiedOn(OffsetDateTime.now(ZoneOffset.UTC));
        cartCacheRepository.save(cart);
        saveCartInDb(cart);
        return cart;
    }

}
