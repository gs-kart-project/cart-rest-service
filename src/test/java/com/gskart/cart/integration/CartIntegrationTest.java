package com.gskart.cart.integration;

import com.gskart.cart.data.repositories.ICartRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Round-trips a cart through the real HTTP + Redis + Kafka + Mongo stack (Testcontainers-backed).
 * The Mongo write happens asynchronously via {@code cart.update} -> UpdateCartConsumer, so this
 * also re-validates the Kafka TYPE_MAPPINGS fix (cart vs orderRequest) end-to-end.
 */
class CartIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    @Qualifier("cartDbRepository")
    private ICartRepository cartDbRepository;

    @Test
    void postCart_roundTripsThroughRedis_andWritesThroughToMongoViaKafka() {
        Map<String, Object> productItem = Map.of(
                "productId", 1,
                "productName", "Widget",
                "quantity", 1,
                "unitPrice", 10.0,
                "totalPrice", 10.0,
                "quantityUnit", "COUNT"
        );
        Map<String, Object> request = Map.of("productItems", List.of(productItem));

        Map<?, ?> createdBody = restTestClient.post().uri("/carts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK)
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        String cartId = (String) createdBody.get("cartId");
        assertThat(cartId).isNotBlank();

        Map<?, ?> fetchedBody = restTestClient.get().uri("/carts/{cartId}", cartId)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK)
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        assertThat(fetchedBody.get("cartId")).isEqualTo(cartId);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(cartDbRepository.findByUsernameAndStatusIsNot("SystemUser", null).isPresent())
                        .isTrue()
        );
    }
}
