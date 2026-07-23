package com.gskart.cart.services;

import com.gskart.cart.DTOs.requests.ContactType;
import com.gskart.cart.data.entities.*;
import com.gskart.cart.data.repositories.ICartRepository;
import com.gskart.cart.exceptions.CartAccessDeniedException;
import com.gskart.cart.exceptions.CartNotFoundException;
import com.gskart.cart.exceptions.DeleteCartException;
import com.gskart.cart.exceptions.UpdateCartException;
import com.gskart.cart.mappers.CartMapper;
import com.gskart.cart.messaging.DomainEvent;
import com.gskart.cart.messaging.outbox.OutboxStore;
import com.gskart.cart.redis.entities.Cart;
import com.gskart.cart.redis.repositories.CartRepository;
import com.gskart.cart.security.models.GSKartResourceServerUser;
import com.gskart.cart.security.models.GSKartResourceServerUserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartCacheRepository;
    @Mock
    private ICartRepository cartDbRepository;
    @Mock
    private OutboxStore outboxStore;
    @Mock
    private GSKartResourceServerUserContext resourceServerUserContext;
    @Mock
    private GSKartResourceServerUser resourceServerUser;
    @Mock
    private CartMapper cartMapper;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartCacheRepository, cartDbRepository, outboxStore,
                resourceServerUserContext, cartMapper);
        lenient().when(resourceServerUserContext.getGskartResourceServerUser()).thenReturn(resourceServerUser);
        lenient().when(resourceServerUser.getUsername()).thenReturn("cart-user");
    }

    private ProductItem productItem(int id) {
        ProductItem productItem = new ProductItem();
        productItem.setProductId(id);
        productItem.setProductName("Widget " + id);
        productItem.setQuantity(1f);
        productItem.setUnitPrice(10.0);
        productItem.setTotalPrice(10.0);
        productItem.setQuantityUnit(QuantityUnit.COUNT);
        return productItem;
    }

    private Cart cartWithProducts() {
        Cart cart = new Cart();
        cart.setId("cart-1");
        cart.setCartUsername("cart-user");
        cart.setProductItems(new ArrayList<>(List.of(productItem(1))));
        return cart;
    }

    // ---- addNewCart ----

    @Test
    void addNewCart_setsIdStatusUserAndDefaultDeliveryDetails_thenSavesAndPublishes() {
        Cart cart = cartWithProducts();
        cart.setId(null);
        when(cartCacheRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        Cart result = cartService.addNewCart(cart);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getStatus()).isEqualTo(CartStatus.CREATED);
        assertThat(result.getCartUsername()).isEqualTo("cart-user");
        assertThat(result.getCreatedBy()).isEqualTo("cart-user");
        assertThat(result.getDeliveryDetails()).hasSize(1);
        assertThat(result.getDeliveryDetails().get(0).getProductIds()).containsExactly(1);

        verify(cartCacheRepository).save(cart);
        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outboxStore).append(captor.capture());
        assertThat(captor.getValue().getDestination()).isEqualTo("cart.update");
        assertThat(captor.getValue().getKey()).isEqualTo(result.getId());
        assertThat(captor.getValue().getPayload()).isEqualTo(result);
    }

    @Test
    void addNewCart_keepsExistingDeliveryDetails_whenAlreadyPresent() {
        Cart cart = cartWithProducts();
        DeliveryDetails existing = new DeliveryDetails();
        existing.setId((short) 9);
        cart.setDeliveryDetails(new ArrayList<>(List.of(existing)));
        when(cartCacheRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        Cart result = cartService.addNewCart(cart);

        assertThat(result.getDeliveryDetails()).hasSize(1);
        assertThat(result.getDeliveryDetails().get(0).getId()).isEqualTo((short) 9);
    }

    @Test
    void addNewCart_throwsCartAccessDeniedException_whenNoAuthenticatedUser() {
        when(resourceServerUserContext.getGskartResourceServerUser()).thenReturn(null);

        assertThatThrownBy(() -> cartService.addNewCart(cartWithProducts()))
                .isInstanceOf(CartAccessDeniedException.class);
        verifyNoInteractions(cartCacheRepository);
    }

    // ---- getCartById ----

    @Test
    void getCartById_returnsCart_whenFoundInRedis() throws CartNotFoundException {
        Cart cart = cartWithProducts();
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));

        Cart result = cartService.getCartById("cart-1");

        assertThat(result).isEqualTo(cart);
    }

    @Test
    void getCartById_throwsCartNotFoundException_whenMissingInRedis() {
        when(cartCacheRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.getCartById("missing"))
                .isInstanceOf(CartNotFoundException.class);
    }

    @Test
    void getCartById_throwsCartAccessDeniedException_whenCartOwnedByAnotherUser() {
        Cart cart = cartWithProducts();
        cart.setCartUsername("someone-else");
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.getCartById("cart-1"))
                .isInstanceOf(CartAccessDeniedException.class);
    }

    @Test
    void getCartById_throwsCartAccessDeniedException_whenNoAuthenticatedUser() {
        Cart cart = cartWithProducts();
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));
        when(resourceServerUserContext.getGskartResourceServerUser()).thenReturn(null);

        assertThatThrownBy(() -> cartService.getCartById("cart-1"))
                .isInstanceOf(CartAccessDeniedException.class);
    }

    // ---- getOpenCartForCurrentUser ----

    @Test
    void getOpenCartForCurrentUser_returnsOpenCart_fromCache() throws CartNotFoundException {
        Cart checkedOut = cartWithProducts();
        checkedOut.setStatus(CartStatus.CHECKED_OUT);
        Cart open = cartWithProducts();
        open.setId("cart-open");
        open.setStatus(CartStatus.CREATED);
        when(cartCacheRepository.findCartsByCartUsername("cart-user")).thenReturn(List.of(checkedOut, open));

        Cart result = cartService.getOpenCartForCurrentUser();

        assertThat(result).isEqualTo(open);
        verifyNoInteractions(cartDbRepository);
    }

    @Test
    void getOpenCartForCurrentUser_fallsBackToMongo_whenCacheEmptyOrAllCheckedOut() throws CartNotFoundException {
        when(cartCacheRepository.findCartsByCartUsername("cart-user")).thenReturn(List.of());
        com.gskart.cart.data.entities.Cart dbCart = new com.gskart.cart.data.entities.Cart();
        dbCart.setId("mongo-1");
        when(cartDbRepository.findByUsernameAndStatusIsNot("cart-user", CartStatus.CHECKED_OUT))
                .thenReturn(Optional.of(dbCart));
        Cart mappedCart = cartWithProducts();
        when(cartMapper.cartDbToCartCacheEntity(dbCart)).thenReturn(mappedCart);
        when(cartCacheRepository.save(mappedCart)).thenReturn(mappedCart);

        Cart result = cartService.getOpenCartForCurrentUser();

        assertThat(result).isEqualTo(mappedCart);
        verify(cartCacheRepository).save(mappedCart);
    }

    @Test
    void getOpenCartForCurrentUser_fallsBackToMongo_whenCacheListIsNull() throws CartNotFoundException {
        when(cartCacheRepository.findCartsByCartUsername("cart-user")).thenReturn(null);
        com.gskart.cart.data.entities.Cart dbCart = new com.gskart.cart.data.entities.Cart();
        dbCart.setId("mongo-1");
        when(cartDbRepository.findByUsernameAndStatusIsNot("cart-user", CartStatus.CHECKED_OUT))
                .thenReturn(Optional.of(dbCart));
        Cart mappedCart = cartWithProducts();
        when(cartMapper.cartDbToCartCacheEntity(dbCart)).thenReturn(mappedCart);
        when(cartCacheRepository.save(mappedCart)).thenReturn(mappedCart);

        Cart result = cartService.getOpenCartForCurrentUser();

        assertThat(result).isEqualTo(mappedCart);
    }

    @Test
    void getOpenCartForCurrentUser_throwsCartNotFoundException_whenAbsentInBoth() {
        when(cartCacheRepository.findCartsByCartUsername("cart-user")).thenReturn(List.of());
        when(cartDbRepository.findByUsernameAndStatusIsNot("cart-user", CartStatus.CHECKED_OUT))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.getOpenCartForCurrentUser())
                .isInstanceOf(CartNotFoundException.class);
    }

    @Test
    void getOpenCartForCurrentUser_throwsCartAccessDeniedException_whenNoAuthenticatedUser() {
        when(resourceServerUserContext.getGskartResourceServerUser()).thenReturn(null);

        assertThatThrownBy(() -> cartService.getOpenCartForCurrentUser())
                .isInstanceOf(CartAccessDeniedException.class);
        verifyNoInteractions(cartCacheRepository, cartDbRepository);
    }

    // ---- addProductsToCart ----

    @Test
    void addProductsToCart_appendsProductsAndSyncsMatchingDeliveryDetails() throws CartNotFoundException {
        Cart cart = cartWithProducts();
        DeliveryDetails deliveryDetails = new DeliveryDetails();
        deliveryDetails.setId((short) 1);
        deliveryDetails.setProductIds(new ArrayList<>(List.of(1)));
        cart.setDeliveryDetails(new ArrayList<>(List.of(deliveryDetails)));
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));
        when(cartCacheRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean result = cartService.addProductsToCart("cart-1", List.of(productItem(2)));

        assertThat(result).isTrue();
        assertThat(cart.getProductItems()).extracting(ProductItem::getProductId).containsExactly(1, 2);
        verify(cartCacheRepository).save(cart);
    }

    @Test
    void addProductsToCart_skipsSync_whenNoDeliveryDetailMatchesDefaultId() throws CartNotFoundException {
        Cart cart = cartWithProducts();
        DeliveryDetails deliveryDetails = new DeliveryDetails();
        deliveryDetails.setId((short) 2);
        deliveryDetails.setProductIds(new ArrayList<>());
        cart.setDeliveryDetails(new ArrayList<>(List.of(deliveryDetails)));
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));
        when(cartCacheRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean result = cartService.addProductsToCart("cart-1", List.of(productItem(2)));

        assertThat(result).isTrue();
        assertThat(deliveryDetails.getProductIds()).isEmpty();
    }

    @Test
    void addProductsToCart_skipsDeliveryDetailsSync_whenNoneExist() throws CartNotFoundException {
        Cart cart = cartWithProducts();
        cart.setDeliveryDetails(null);
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));
        when(cartCacheRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean result = cartService.addProductsToCart("cart-1", List.of(productItem(2)));

        assertThat(result).isTrue();
        assertThat(cart.getDeliveryDetails()).isNull();
    }

    // ---- updateProductsInCart / deleteProductsFromCart ----

    @Test
    void updateProductsInCart_replacesMatchingProductsAndUpdatesDeliveryDetails() throws CartNotFoundException {
        Cart cart = cartWithProducts();
        DeliveryDetails deliveryDetails = new DeliveryDetails();
        deliveryDetails.setId((short) 1);
        deliveryDetails.setProductIds(new ArrayList<>(List.of(1)));
        cart.setDeliveryDetails(new ArrayList<>(List.of(deliveryDetails)));
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));
        when(cartCacheRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductItem updated = productItem(1);
        updated.setProductName("Updated Widget");
        boolean result = cartService.updateProductsInCart("cart-1", List.of(updated));

        assertThat(result).isTrue();
        assertThat(cart.getProductItems()).hasSize(1);
        assertThat(cart.getProductItems().get(0).getProductName()).isEqualTo("Updated Widget");
        assertThat(cart.getStatus()).isEqualTo(CartStatus.APPENDED);
        verify(outboxStore).append(any(DomainEvent.class));
    }

    @Test
    void updateProductsInCart_skipsSync_whenNoDeliveryDetailMatchesDefaultId() throws CartNotFoundException {
        Cart cart = cartWithProducts();
        DeliveryDetails deliveryDetails = new DeliveryDetails();
        deliveryDetails.setId((short) 2);
        deliveryDetails.setProductIds(new ArrayList<>());
        cart.setDeliveryDetails(new ArrayList<>(List.of(deliveryDetails)));
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));
        when(cartCacheRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean result = cartService.updateProductsInCart("cart-1", List.of(productItem(1)));

        assertThat(result).isTrue();
        assertThat(deliveryDetails.getProductIds()).isEmpty();
    }

    @Test
    void updateProductsInCart_skipsDeliveryDetailsSync_whenNoneExist() throws CartNotFoundException {
        Cart cart = cartWithProducts();
        cart.setDeliveryDetails(null);
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));
        when(cartCacheRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean result = cartService.updateProductsInCart("cart-1", List.of(productItem(1)));

        assertThat(result).isTrue();
        assertThat(cart.getDeliveryDetails()).isNull();
    }

    @Test
    void updateProductsInCart_throwsCartNotFoundException_whenCartMissing() {
        when(cartCacheRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.updateProductsInCart("missing", List.of(productItem(1))))
                .isInstanceOf(CartNotFoundException.class);
    }

    @Test
    void deleteProductsFromCart_removesMatchingProducts() throws CartNotFoundException {
        Cart cart = cartWithProducts();
        cart.getProductItems().add(productItem(2));
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));
        when(cartCacheRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean result = cartService.deleteProductsFromCart("cart-1", List.of(1));

        assertThat(result).isTrue();
        assertThat(cart.getProductItems()).extracting(ProductItem::getProductId).containsExactly(2);
        assertThat(cart.getStatus()).isEqualTo(CartStatus.APPENDED);
    }

    @Test
    void deleteProductsFromCart_throwsCartNotFoundException_whenCartMissing() {
        when(cartCacheRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.deleteProductsFromCart("missing", List.of(1)))
                .isInstanceOf(CartNotFoundException.class);
    }

    // ---- updateDeliveryContact ----

    private Contact contact(short id) {
        Contact contact = new Contact();
        contact.setId(id);
        contact.setFirstName("Jane");
        contact.setLastName("Doe");
        return contact;
    }

    @Test
    void updateDeliveryContact_setsBillingContact_creatingDefaultDeliveryDetails() throws Exception {
        Cart cart = cartWithProducts();
        cart.setDeliveryDetails(null);
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));
        when(cartCacheRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean result = cartService.updateDeliveryContact("cart-1", (short) 1, contact((short) 5), ContactType.BILLING);

        assertThat(result).isTrue();
        assertThat(cart.getDeliveryDetails()).hasSize(1);
        assertThat(cart.getDeliveryDetails().get(0).getBillingContact().getId()).isEqualTo((short) 5);
    }

    @Test
    void updateDeliveryContact_setsShippingContact_onExistingDeliveryDetails() throws Exception {
        Cart cart = cartWithProducts();
        DeliveryDetails deliveryDetails = new DeliveryDetails();
        deliveryDetails.setId((short) 1);
        cart.setDeliveryDetails(new ArrayList<>(List.of(deliveryDetails)));
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));
        when(cartCacheRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        cartService.updateDeliveryContact("cart-1", (short) 1, contact((short) 5), ContactType.SHIPPING);

        assertThat(deliveryDetails.getShippingContact().getId()).isEqualTo((short) 5);
    }

    @Test
    void updateDeliveryContact_addsNewSecondaryContact() throws Exception {
        Cart cart = cartWithProducts();
        DeliveryDetails deliveryDetails = new DeliveryDetails();
        deliveryDetails.setId((short) 1);
        deliveryDetails.setSecondaryContacts(new ArrayList<>());
        cart.setDeliveryDetails(new ArrayList<>(List.of(deliveryDetails)));
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));
        when(cartCacheRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        cartService.updateDeliveryContact("cart-1", (short) 1, contact((short) 5), ContactType.SECONDARY);

        assertThat(deliveryDetails.getSecondaryContacts()).hasSize(1);
        assertThat(deliveryDetails.getSecondaryContacts().get(0).getId()).isEqualTo((short) 5);
    }

    @Test
    void updateDeliveryContact_updatesExistingSecondaryContact() throws Exception {
        Cart cart = cartWithProducts();
        DeliveryDetails deliveryDetails = new DeliveryDetails();
        deliveryDetails.setId((short) 1);
        Contact existingSecondary = contact((short) 5);
        deliveryDetails.setSecondaryContacts(new ArrayList<>(List.of(existingSecondary)));
        cart.setDeliveryDetails(new ArrayList<>(List.of(deliveryDetails)));
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));
        when(cartCacheRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        Contact updated = contact((short) 5);
        updated.setFirstName("Updated");
        cartService.updateDeliveryContact("cart-1", (short) 1, updated, ContactType.SECONDARY);

        assertThat(deliveryDetails.getSecondaryContacts()).hasSize(1);
        assertThat(deliveryDetails.getSecondaryContacts().get(0).getFirstName()).isEqualTo("Updated");
    }

    @Test
    void updateDeliveryContact_throwsUpdateCartException_whenContactIdMissing() throws CartNotFoundException {
        Cart cart = cartWithProducts();
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.updateDeliveryContact("cart-1", (short) 1, contact((short) 0), ContactType.BILLING))
                .isInstanceOf(UpdateCartException.class);
    }

    @Test
    void updateDeliveryContact_throwsUpdateCartException_whenDeliveryDetailIdUnknown() throws CartNotFoundException {
        Cart cart = cartWithProducts();
        DeliveryDetails deliveryDetails = new DeliveryDetails();
        deliveryDetails.setId((short) 1);
        cart.setDeliveryDetails(new ArrayList<>(List.of(deliveryDetails)));
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.updateDeliveryContact("cart-1", (short) 99, contact((short) 5), ContactType.BILLING))
                .isInstanceOf(UpdateCartException.class);
    }

    // ---- deleteContact ----

    @Test
    void deleteContact_removesBillingContact() throws Exception {
        Cart cart = cartWithProducts();
        DeliveryDetails deliveryDetails = new DeliveryDetails();
        deliveryDetails.setId((short) 1);
        deliveryDetails.setBillingContact(contact((short) 5));
        cart.setDeliveryDetails(new ArrayList<>(List.of(deliveryDetails)));
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));
        when(cartCacheRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean result = cartService.deleteContact("cart-1", (short) 1, (short) 5, ContactType.BILLING);

        assertThat(result).isTrue();
        assertThat(deliveryDetails.getBillingContact()).isNull();
    }

    @Test
    void deleteContact_removesMatchingSecondaryContact() throws Exception {
        Cart cart = cartWithProducts();
        DeliveryDetails deliveryDetails = new DeliveryDetails();
        deliveryDetails.setId((short) 1);
        deliveryDetails.setSecondaryContacts(new ArrayList<>(List.of(contact((short) 5))));
        cart.setDeliveryDetails(new ArrayList<>(List.of(deliveryDetails)));
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));
        when(cartCacheRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        cartService.deleteContact("cart-1", (short) 1, (short) 5, ContactType.SECONDARY);

        assertThat(deliveryDetails.getSecondaryContacts()).isEmpty();
    }

    @Test
    void deleteContact_throwsUpdateCartException_whenSecondaryContactUnknown() throws CartNotFoundException {
        Cart cart = cartWithProducts();
        DeliveryDetails deliveryDetails = new DeliveryDetails();
        deliveryDetails.setId((short) 1);
        deliveryDetails.setSecondaryContacts(new ArrayList<>());
        cart.setDeliveryDetails(new ArrayList<>(List.of(deliveryDetails)));
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.deleteContact("cart-1", (short) 1, (short) 5, ContactType.SECONDARY))
                .isInstanceOf(UpdateCartException.class);
    }

    @Test
    void deleteContact_throwsDeleteCartException_whenDeliveryDetailsMissing() {
        Cart cart = cartWithProducts();
        cart.setDeliveryDetails(null);
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.deleteContact("cart-1", (short) 1, (short) 5, ContactType.BILLING))
                .isInstanceOf(DeleteCartException.class);
    }

    @Test
    void deleteContact_throwsDeleteCartException_whenDeliveryDetailIdUnknown() {
        Cart cart = cartWithProducts();
        DeliveryDetails deliveryDetails = new DeliveryDetails();
        deliveryDetails.setId((short) 1);
        cart.setDeliveryDetails(new ArrayList<>(List.of(deliveryDetails)));
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.deleteContact("cart-1", (short) 99, (short) 5, ContactType.BILLING))
                .isInstanceOf(DeleteCartException.class);
    }

    // ---- checkout ----

    private Cart checkoutReadyCart() {
        Cart cart = cartWithProducts();
        DeliveryDetails deliveryDetails = new DeliveryDetails();
        deliveryDetails.setId((short) 1);
        deliveryDetails.setProductIds(List.of(1));
        deliveryDetails.setBillingContact(contact((short) 1));
        deliveryDetails.setShippingContact(contact((short) 2));
        cart.setDeliveryDetails(new ArrayList<>(List.of(deliveryDetails)));
        return cart;
    }

    @Test
    void checkout_succeeds_setsStatusAndPlacesOrder() throws Exception {
        Cart cart = checkoutReadyCart();
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));
        when(cartCacheRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
        com.gskart.cart.DTOs.orderService.requests.OrderRequest orderRequest =
                new com.gskart.cart.DTOs.orderService.requests.OrderRequest();
        orderRequest.setCartId("cart-1");
        when(cartMapper.cartRedisEntityToOrderRequest(cart)).thenReturn(orderRequest);

        String resultId = cartService.checkout("cart-1");

        assertThat(resultId).isEqualTo("cart-1");
        assertThat(cart.getStatus()).isEqualTo(CartStatus.CHECKED_OUT);
        // one outbox event for saveCartInDb (cart.update), one for placeOrder (order.place)
        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outboxStore, times(2)).append(captor.capture());
        assertThat(captor.getAllValues()).extracting(DomainEvent::getDestination)
                .containsExactlyInAnyOrder("cart.update", "order.place");
    }

    @Test
    void checkout_throwsUpdateCartException_whenNoProducts() {
        Cart cart = new Cart();
        cart.setId("cart-1");
        cart.setCartUsername("cart-user");
        cart.setProductItems(new ArrayList<>());
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.checkout("cart-1"))
                .isInstanceOf(UpdateCartException.class)
                .hasMessageContaining("no products selected");
    }

    @Test
    void checkout_throwsUpdateCartException_whenProductsListIsNull() {
        Cart cart = new Cart();
        cart.setId("cart-1");
        cart.setCartUsername("cart-user");
        cart.setProductItems(null);
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.checkout("cart-1"))
                .isInstanceOf(UpdateCartException.class)
                .hasMessageContaining("no products selected");
    }

    @Test
    void checkout_throwsUpdateCartException_whenNoDeliveryDetails() {
        Cart cart = cartWithProducts();
        cart.setDeliveryDetails(null);
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.checkout("cart-1"))
                .isInstanceOf(UpdateCartException.class)
                .hasMessageContaining("Delivery details are not updated");
    }

    @Test
    void checkout_throwsUpdateCartException_whenDeliveryDetailsListIsEmpty() {
        Cart cart = cartWithProducts();
        cart.setDeliveryDetails(new ArrayList<>());
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.checkout("cart-1"))
                .isInstanceOf(UpdateCartException.class)
                .hasMessageContaining("Delivery details are not updated");
    }

    @Test
    void checkout_throwsUpdateCartException_whenBillingAndShippingContactsMissing() {
        Cart cart = cartWithProducts();
        DeliveryDetails deliveryDetails = new DeliveryDetails();
        deliveryDetails.setId((short) 1);
        deliveryDetails.setProductIds(List.of(1));
        cart.setDeliveryDetails(new ArrayList<>(List.of(deliveryDetails)));
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.checkout("cart-1"))
                .isInstanceOf(UpdateCartException.class)
                .hasMessageContaining("Billing contacts missing")
                .hasMessageContaining("Shipping contacts missing");
    }

    @Test
    void checkout_throwsUpdateCartException_whenOnlyProductIdsMissing() {
        Cart cart = cartWithProducts();
        DeliveryDetails deliveryDetails = new DeliveryDetails();
        deliveryDetails.setId((short) 1);
        deliveryDetails.setProductIds(null);
        deliveryDetails.setBillingContact(contact((short) 1));
        deliveryDetails.setShippingContact(contact((short) 2));
        cart.setDeliveryDetails(new ArrayList<>(List.of(deliveryDetails)));
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.checkout("cart-1"))
                .isInstanceOf(UpdateCartException.class)
                .hasMessageContaining("Product ids missing")
                .satisfies(e -> assertThat(e.getMessage())
                        .doesNotContain("Billing contacts missing")
                        .doesNotContain("Shipping contacts missing"));
    }

    @Test
    void checkout_joinsMultipleMissingIdsWithComma() {
        Cart cart = cartWithProducts();
        DeliveryDetails deliveryDetails1 = new DeliveryDetails();
        deliveryDetails1.setId((short) 1);
        deliveryDetails1.setProductIds(List.of(1));
        deliveryDetails1.setShippingContact(contact((short) 2));
        DeliveryDetails deliveryDetails2 = new DeliveryDetails();
        deliveryDetails2.setId((short) 2);
        deliveryDetails2.setProductIds(List.of(1));
        deliveryDetails2.setShippingContact(contact((short) 2));
        cart.setDeliveryDetails(new ArrayList<>(List.of(deliveryDetails1, deliveryDetails2)));
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.checkout("cart-1"))
                .isInstanceOf(UpdateCartException.class)
                .hasMessageContaining("Billing contacts missing in delivery details ids: 1, 2");
    }

    // ---- updateOrderDetails / updatePaymentDetails ----
    // These run on background messaging threads (no request-scoped user), so they take
    // modifiedBy explicitly rather than reading GSKartResourceServerUserContext.

    @Test
    void updateOrderDetails_setsNewOrderDetails_whenNoneExist() throws CartNotFoundException {
        Cart cart = cartWithProducts();
        cart.setOrderDetails(null);
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));
        when(cartCacheRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderDetails orderDetails = new OrderDetails();
        orderDetails.setOrderId(42);
        orderDetails.setOrderStatus(OrderStatus.ORDER_PLACED);

        Cart result = cartService.updateOrderDetails("cart-1", orderDetails, "order-placer");

        assertThat(result.getOrderDetails()).isEqualTo(orderDetails);
        assertThat(result.getModifiedBy()).isEqualTo("order-placer");
    }

    @Test
    void updateOrderDetails_updatesExistingOrderDetails() throws CartNotFoundException {
        Cart cart = cartWithProducts();
        OrderDetails existing = new OrderDetails();
        existing.setOrderId(1);
        existing.setOrderStatus(OrderStatus.COULD_NOT_PLACE_ORDER);
        cart.setOrderDetails(existing);
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));
        when(cartCacheRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderDetails update = new OrderDetails();
        update.setOrderId(99);
        update.setOrderStatus(OrderStatus.ORDER_PLACED);

        Cart result = cartService.updateOrderDetails("cart-1", update, "order-placer");

        assertThat(result.getOrderDetails().getOrderId()).isEqualTo(99);
        assertThat(result.getOrderDetails().getOrderStatus()).isEqualTo(OrderStatus.ORDER_PLACED);
    }

    @Test
    void updateOrderDetails_usesPassedModifiedBy_withoutTouchingRequestContext() throws CartNotFoundException {
        // Simulates the Kafka consumer / producer-callback threads, which have no request-scoped user.
        Cart cart = cartWithProducts();
        cart.setOrderDetails(null);
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));
        when(cartCacheRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderDetails orderDetails = new OrderDetails();
        orderDetails.setOrderStatus(OrderStatus.ORDER_PLACED);

        Cart result = cartService.updateOrderDetails("cart-1", orderDetails, "cart-user");

        assertThat(result.getModifiedBy()).isEqualTo("cart-user");
        verifyNoInteractions(resourceServerUser);
    }

    @Test
    void updatePaymentDetails_setsNewPaymentDetails_whenNoneExist() throws CartNotFoundException {
        Cart cart = cartWithProducts();
        cart.setPaymentDetails(null);
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));
        when(cartCacheRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentDetails paymentDetails = new PaymentDetails();
        paymentDetails.setPaymentId(7);
        paymentDetails.setPaymentStatus(PaymentStatus.COMPLETED);

        Cart result = cartService.updatePaymentDetails("cart-1", paymentDetails, "cart-user");

        assertThat(result.getPaymentDetails()).isEqualTo(paymentDetails);
        assertThat(result.getModifiedBy()).isEqualTo("cart-user");
    }

    @Test
    void updatePaymentDetails_updatesExistingPaymentDetails() throws CartNotFoundException {
        Cart cart = cartWithProducts();
        PaymentDetails existing = new PaymentDetails();
        existing.setPaymentId(1);
        existing.setPaymentStatus(PaymentStatus.INITIATED);
        cart.setPaymentDetails(existing);
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));
        when(cartCacheRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentDetails update = new PaymentDetails();
        update.setPaymentId(55);
        update.setPaymentStatus(PaymentStatus.COMPLETED);

        Cart result = cartService.updatePaymentDetails("cart-1", update, "cart-user");

        assertThat(result.getPaymentDetails().getPaymentId()).isEqualTo(55);
        assertThat(result.getPaymentDetails().getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    // ---- getCartForSystem (background reads, no ownership check) ----

    @Test
    void getCartForSystem_returnsCart_withoutOwnershipOrUserContext() throws CartNotFoundException {
        Cart cart = cartWithProducts();
        cart.setCartUsername("someone-else");
        when(cartCacheRepository.findById("cart-1")).thenReturn(Optional.of(cart));

        Cart result = cartService.getCartForSystem("cart-1");

        assertThat(result).isEqualTo(cart);
        verifyNoInteractions(resourceServerUserContext, resourceServerUser);
    }

    @Test
    void getCartForSystem_throwsCartNotFoundException_whenMissing() {
        when(cartCacheRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.getCartForSystem("missing"))
                .isInstanceOf(CartNotFoundException.class);
    }
}
