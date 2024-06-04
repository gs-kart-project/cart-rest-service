package com.gskart.cart.data.repositories;

import com.gskart.cart.data.entities.Cart;
import com.gskart.cart.data.entities.CartStatus;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;
import static org.springframework.data.mongodb.core.query.Update.update;

@Repository("cartDbRepository")
public class CartRepository implements ICartRepository {
    private final MongoTemplate mongoTemplate;

    public CartRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
        //createCollection(MongoConstants.CollectionNames.CART);
    }

    private void createCollection(String collectionName) {
        if(!this.mongoTemplate.collectionExists(collectionName)) {
            this.mongoTemplate.createCollection(collectionName);
        }
    }

    @Override
    public Cart save(Cart cart) {
        cart = this.mongoTemplate.insert(cart);
        return cart;
    }

    @Override
    public Cart update(Cart cart) {
        Update updateDefinition = new Update().set("productItems", cart.getProductItems())
                .set("status", cart.getStatus())
                .set("paymentDetails", cart.getPaymentDetails())
                .set("orderDetails", cart.getOrderDetails())
                .set("deliveryDetails", cart.getDeliveryDetails())
                .set("modifiedBy", cart.getModifiedBy())
                .set("modifiedOn", cart.getModifiedOn());

       Cart updatedCart = this.mongoTemplate.update(Cart.class)
               .matching(query(where("_id").is(cart.getId())))
               .apply(updateDefinition)
               .withOptions(FindAndModifyOptions.options().returnNew(true))
               .findAndModifyValue();
       return updatedCart;
    }

    @Override
    public Optional<Cart> findById(String id) {
        Cart cart = this.mongoTemplate.findById(id, Cart.class);
        return Optional.ofNullable(cart);
    }

    @Override
    public Optional<Cart> findByUsernameAndStatusIsNot(String username, CartStatus status) {
        Cart cart = this.mongoTemplate.findOne(
                query(
                    where("cartUsername")
                        .is(username)
                        .andOperator(where("status").ne(status))),
                Cart.class);

        return Optional.ofNullable(cart);
    }

    @Override
    public List<Cart> findAll() {
        return this.mongoTemplate.findAll(Cart.class);
    }

    @Override
    public void deleteById(String id) {
        this.mongoTemplate.remove(query(where("_id").is(id)) , Cart.class);
    }
}
