package com.gskart.cart.services;

import com.gskart.cart.DTOs.requests.ContactType;
import com.gskart.cart.data.entities.CartStatus;
import com.gskart.cart.data.entities.Contact;
import com.gskart.cart.data.entities.DeliveryDetails;
import com.gskart.cart.data.entities.ProductItem;
import com.gskart.cart.data.repositories.ICartRepository;
import com.gskart.cart.exceptions.CartNotFoundException;
import com.gskart.cart.exceptions.UpdateCartException;
import com.gskart.cart.kafka.constants.KafkaConstants;
import com.gskart.cart.mappers.CartMapper;
import com.gskart.cart.redis.entities.Cart;
import com.gskart.cart.redis.repositories.CartRepository;
import com.gskart.cart.security.models.GSKartResourceServerUserContext;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
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
    public boolean updateDeliveryContact(String cartId, Contact contact, ContactType contactType) throws CartNotFoundException, UpdateCartException {
        Cart cart = getCartById(cartId);

        if(contact.getId() == null || contact.getId() == 0){
            throw new UpdateCartException(String.format("Cart id:%s. Cannot add secondary contact without Id.", cartId));
        }

        if(cart.getDeliveryDetails() == null){
            DeliveryDetails deliveryDetails = new DeliveryDetails();
            deliveryDetails.setSecondaryContacts(new ArrayList<>());
            cart.setDeliveryDetails(new DeliveryDetails());
        }

        switch(contactType){
            case BILLING -> cart.getDeliveryDetails().setBillingContact(contact);
            case SHIPPING -> cart.getDeliveryDetails().setShippingContact(contact);
            case SECONDARY ->{
                Optional<Contact> secondaryContactOptional = cart.getDeliveryDetails().getSecondaryContacts()
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
                    cart.getDeliveryDetails().getSecondaryContacts().add(contact);
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
    public boolean deleteContact(String cartId, Short contactId, ContactType contactType) throws CartNotFoundException, UpdateCartException {
        Cart cart = getCartById(cartId);
        switch(contactType){
            case BILLING -> cart.getDeliveryDetails().setBillingContact(null);
            case SHIPPING -> cart.getDeliveryDetails().setShippingContact(null);
            case SECONDARY -> {
               Optional<Contact> secondaryContactOptional = cart.getDeliveryDetails().getSecondaryContacts().stream().filter(existingContact -> Objects.equals(existingContact.getId(), contactId)).findFirst();
               if(secondaryContactOptional.isPresent()){
                   cart.getDeliveryDetails().getSecondaryContacts().remove(secondaryContactOptional.get());
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

}
