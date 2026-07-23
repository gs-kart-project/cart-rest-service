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
 * The cart mutation is enqueued to the Redis outbox, the relay publishes {@code cart.update}, and
 * {@code CartWriteThroughHandler} writes it through to Mongo — so this exercises the whole outbox path
 * and re-validates the Kafka TYPE_MAPPINGS mapping (cart vs orderRequest) end-to-end.
 */
class CartIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    @Qualifier("cartDbRepository")
    private ICartRepository cartDbRepository;

    private Map<String, Object> newCartRequest() {
        Map<String, Object> productItem = Map.of(
                "productId", 1,
                "productName", "Widget",
                "quantity", 1,
                "unitPrice", 10.0,
                "totalPrice", 10.0,
                "quantityUnit", "COUNT"
        );
        return Map.of("productItems", List.of(productItem));
    }

    @Test
    void postCart_roundTripsThroughRedis_andWritesThroughToMongoViaKafka() {
        Map<?, ?> createdBody = restTestClient.post().uri("/api/v1/carts")
                .header("Authorization", "Bearer " + bearerTokenFor("it-user"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(newCartRequest())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK)
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        String cartId = (String) createdBody.get("cartId");
        assertThat(cartId).isNotBlank();

        Map<?, ?> fetchedBody = restTestClient.get().uri("/api/v1/carts/{cartId}", cartId)
                .header("Authorization", "Bearer " + bearerTokenFor("it-user"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK)
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        assertThat(fetchedBody.get("cartId")).isEqualTo(cartId);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(cartDbRepository.findByUsernameAndStatusIsNot("it-user", null).isPresent())
                        .isTrue()
        );
    }

    @Test
    void postCart_returns401_withoutABearerToken() {
        restTestClient.post().uri("/api/v1/carts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(newCartRequest())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getCart_returns403_forACartOwnedByAnotherUser() {
        Map<?, ?> createdBody = restTestClient.post().uri("/api/v1/carts")
                .header("Authorization", "Bearer " + bearerTokenFor("owner-user"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(newCartRequest())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK)
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        String cartId = (String) createdBody.get("cartId");

        restTestClient.get().uri("/api/v1/carts/{cartId}", cartId)
                .header("Authorization", "Bearer " + bearerTokenFor("other-user"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }
}
