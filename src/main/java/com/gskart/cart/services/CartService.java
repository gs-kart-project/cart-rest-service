package com.gskart.cart.services;

import com.gskart.cart.data.entities.CartStatus;
import com.gskart.cart.data.entities.ProductItem;
import com.gskart.cart.data.repositories.ICartRepository;
import com.gskart.cart.exceptions.CartNotFoundException;
import com.gskart.cart.kafka.constants.KafkaConstants;
import com.gskart.cart.mappers.CartMapper;
import com.gskart.cart.redis.entities.Cart;
import com.gskart.cart.redis.repositories.CartRepository;
import com.gskart.cart.security.models.GSKartResourceServerUserContext;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.data.redis.core.RedisKeyValueTemplate;
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

    private final RedisKeyValueTemplate redisKeyValueTemplate;

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final GSKartResourceServerUserContext resourceServerUserContext;
    private final CartMapper cartMapper;

    public CartService(CartRepository cartCacheRepository, ICartRepository cartDbRepository, RedisKeyValueTemplate redisKeyValueTemplate, KafkaTemplate<String, Object> kafkaTemplate, GSKartResourceServerUserContext resourceServerUserContext, CartMapper cartMapper) {
        this.cartCacheRepository = cartCacheRepository;
        this.cartDbRepository = cartDbRepository;
        this.redisKeyValueTemplate = redisKeyValueTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.resourceServerUserContext = resourceServerUserContext;
        this.cartMapper = cartMapper;
    }

    @Override
    public Cart addNewCart(Cart cart) {
        cart.setCreatedOn(OffsetDateTime.now(ZoneOffset.UTC));
        cart.setId(UUID.randomUUID().toString());
        cart.setStatus(CartStatus.CREATED);
        cart.setCartUsername(resourceServerUserContext.getGskartResourceServerUser().getUsername());
        cart.setCreatedBy(resourceServerUserContext.getGskartResourceServerUser().getUsername());
        Cart savedCart = cartCacheRepository.save(cart);
        saveCartInDb(savedCart);
        return savedCart;
    }

    @Override
    public boolean addProductsToCart(String cartId, List<ProductItem> productItemList) throws CartNotFoundException {
        Cart cart = getCartById(cartId);
        List<ProductItem> productItemListExisting = cart.getProductItems();
        productItemListExisting.addAll(productItemList);
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
        Optional<Cart> cartOptional = cartCacheRepository.findByCartUsernameAndStatusIsNot(username, CartStatus.CHECKED_OUT);
                // In case Cart does not exist in cart, the user will check in Db.
        if(cartOptional.isPresent()){
            return cartOptional.get();
        }
        System.out.printf("Cart not found for user %s in cache, looking up from Db.", username);
        Optional<com.gskart.cart.data.entities.Cart> cartDbEntityOptional = cartDbRepository.findByUsernameAndStatus(username, CartStatus.CHECKED_OUT);
        if(cartDbEntityOptional.isEmpty()){
            throw new CartNotFoundException(String.format("Cart doesn't exist for user: %s in both cache and database", username));
        }

        return cartCacheRepository.save(cartMapper.cartDbToCartCacheEntity(cartDbEntityOptional.get()));
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

    /*
    3. update Delivery details
    4. Add contact
    5. Update contact
    6. Remove contact
     */

}
