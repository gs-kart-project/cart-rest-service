package com.gskart.cart.data.repositories;

import com.gskart.cart.data.entities.Cart;
import com.gskart.cart.data.entities.CartStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.ExecutableUpdateOperation;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartRepositoryTest {

    @Mock
    private MongoTemplate mongoTemplate;

    private CartRepository cartRepository;

    @BeforeEach
    void setUp() {
        cartRepository = new CartRepository(mongoTemplate);
    }

    @Test
    void save_insertsCartViaMongoTemplate() {
        Cart cart = new Cart();
        Cart inserted = new Cart();
        inserted.setId("mongo-1");
        when(mongoTemplate.insert(cart)).thenReturn(inserted);

        Cart result = cartRepository.save(cart);

        assertThat(result.getId()).isEqualTo("mongo-1");
        verify(mongoTemplate).insert(cart);
    }

    @SuppressWarnings("unchecked")
    @Test
    void update_appliesFieldsAndReturnsUpdatedCart() {
        Cart cart = new Cart();
        cart.setId("mongo-1");
        cart.setStatus(CartStatus.APPENDED);

        ExecutableUpdateOperation.ExecutableUpdate<Cart> executableUpdate = mock(ExecutableUpdateOperation.ExecutableUpdate.class);
        ExecutableUpdateOperation.UpdateWithUpdate<Cart> updateWithUpdate = mock(ExecutableUpdateOperation.UpdateWithUpdate.class);
        ExecutableUpdateOperation.TerminatingUpdate<Cart> terminatingUpdate = mock(ExecutableUpdateOperation.TerminatingUpdate.class);
        ExecutableUpdateOperation.TerminatingFindAndModify<Cart> terminatingFindAndModify = mock(ExecutableUpdateOperation.TerminatingFindAndModify.class);
        Cart updatedCart = new Cart();
        updatedCart.setId("mongo-1");
        updatedCart.setStatus(CartStatus.APPENDED);

        when(mongoTemplate.update(Cart.class)).thenReturn(executableUpdate);
        when(executableUpdate.matching(any(Query.class))).thenReturn(updateWithUpdate);
        when(updateWithUpdate.apply(any(Update.class))).thenReturn(terminatingUpdate);
        when(terminatingUpdate.withOptions(any(FindAndModifyOptions.class))).thenReturn(terminatingFindAndModify);
        when(terminatingFindAndModify.findAndModifyValue()).thenReturn(updatedCart);

        Cart result = cartRepository.update(cart);

        assertThat(result).isEqualTo(updatedCart);
    }

    @Test
    void findById_returnsCart_whenPresent() {
        Cart cart = new Cart();
        cart.setId("mongo-1");
        when(mongoTemplate.findById("mongo-1", Cart.class)).thenReturn(cart);

        Optional<Cart> result = cartRepository.findById("mongo-1");

        assertThat(result).contains(cart);
    }

    @Test
    void findById_returnsEmpty_whenAbsent() {
        when(mongoTemplate.findById("missing", Cart.class)).thenReturn(null);

        Optional<Cart> result = cartRepository.findById("missing");

        assertThat(result).isEmpty();
    }

    @Test
    void findByUsernameAndStatusIsNot_returnsCart_whenFound() {
        Cart cart = new Cart();
        cart.setId("mongo-1");
        when(mongoTemplate.findOne(any(Query.class), eq(Cart.class))).thenReturn(cart);

        Optional<Cart> result = cartRepository.findByUsernameAndStatusIsNot("SystemUser", CartStatus.CHECKED_OUT);

        assertThat(result).contains(cart);
    }

    @Test
    void findByUsernameAndStatusIsNot_returnsEmpty_whenNotFound() {
        when(mongoTemplate.findOne(any(Query.class), eq(Cart.class))).thenReturn(null);

        Optional<Cart> result = cartRepository.findByUsernameAndStatusIsNot("SystemUser", CartStatus.CHECKED_OUT);

        assertThat(result).isEmpty();
    }

    @Test
    void findAll_delegatesToMongoTemplate() {
        Cart cart = new Cart();
        when(mongoTemplate.findAll(Cart.class)).thenReturn(List.of(cart));

        List<Cart> result = cartRepository.findAll();

        assertThat(result).containsExactly(cart);
    }

    @Test
    void deleteById_removesMatchingDocument() {
        cartRepository.deleteById("mongo-1");

        verify(mongoTemplate).remove(any(Query.class), eq(Cart.class));
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
