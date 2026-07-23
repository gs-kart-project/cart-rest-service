package com.gskart.cart.messaging.outbox;

import com.gskart.cart.messaging.DomainEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisOutboxStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ZSetOperations<String, String> zSetOps;
    @Mock
    private ListOperations<String, String> listOps;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private RedisOutboxStore store;

    @BeforeEach
    void setUp() {
        store = new RedisOutboxStore(redisTemplate, objectMapper);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        lenient().when(redisTemplate.opsForList()).thenReturn(listOps);
    }

    private DomainEvent event() {
        return DomainEvent.builder()
                .destination("cart.update")
                .key("cart-1")
                .eventType("CART_UPDATE")
                .payload("hello")
                .build();
    }

    private OutboxEntry entry(int attempts) {
        return new OutboxEntry("id-1", "cart.update", "cart-1", "CART_UPDATE",
                "java.lang.String", "\"hello\"", 0L, attempts);
    }

    private String serialize(OutboxEntry entry) {
        return objectMapper.writeValueAsString(entry);
    }

    @Test
    void append_addsPendingEntry_eligibleImmediately_carryingRoutingAndSerializedPayload() {
        store.append(event());

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(zSetOps).add(eq(RedisOutboxStore.PENDING_KEY), jsonCaptor.capture(), anyDouble());
        OutboxEntry entry = objectMapper.readValue(jsonCaptor.getValue(), OutboxEntry.class);
        assertThat(entry.getId()).isNotBlank();
        assertThat(entry.getDestination()).isEqualTo("cart.update");
        assertThat(entry.getKey()).isEqualTo("cart-1");
        assertThat(entry.getEventType()).isEqualTo("CART_UPDATE");
        assertThat(entry.getPayloadType()).isEqualTo("java.lang.String");
        assertThat(entry.getPayload()).isEqualTo("\"hello\"");
        assertThat(entry.getAttempts()).isZero();
    }

    @Test
    void claimDue_returnsEntriesWhoseRetryTimeHasArrived() {
        when(zSetOps.rangeByScore(eq(RedisOutboxStore.PENDING_KEY), eq(0d), anyDouble(), eq(0L), eq(10L)))
                .thenReturn(new LinkedHashSet<>(List.of(serialize(entry(0)))));

        List<OutboxEntry> claimed = store.claimDue(10);

        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).getId()).isEqualTo("id-1");
    }

    @Test
    void claimDue_returnsEmpty_whenNothingDue() {
        when(zSetOps.rangeByScore(anyString(), anyDouble(), anyDouble(), anyLong(), anyLong()))
                .thenReturn(null);

        assertThat(store.claimDue(10)).isEmpty();
    }

    @Test
    void markPublished_removesEntryFromPending() {
        OutboxEntry entry = entry(0);

        store.markPublished(entry);

        verify(zSetOps).remove(RedisOutboxStore.PENDING_KEY, serialize(entry));
    }

    @Test
    void reschedule_removesOldEntryAndReAddsWithIncrementedAttemptsAndLaterScore() {
        OutboxEntry entry = entry(1);
        String originalJson = serialize(entry);

        store.reschedule(entry, 20000);

        verify(zSetOps).remove(RedisOutboxStore.PENDING_KEY, originalJson);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(zSetOps).add(eq(RedisOutboxStore.PENDING_KEY), jsonCaptor.capture(), anyDouble());
        OutboxEntry rescheduled = objectMapper.readValue(jsonCaptor.getValue(), OutboxEntry.class);
        assertThat(rescheduled.getAttempts()).isEqualTo(2);
    }

    @Test
    void deadLetter_removesFromPendingAndPushesToDeadList() {
        OutboxEntry entry = entry(5);
        String json = serialize(entry);

        store.deadLetter(entry);

        verify(zSetOps).remove(RedisOutboxStore.PENDING_KEY, json);
        verify(listOps).rightPush(RedisOutboxStore.DEAD_KEY, json);
    }

    @Test
    void toDomainEvent_reconstructsEventWithDeserializedPayload() {
        DomainEvent reconstructed = store.toDomainEvent(entry(0));

        assertThat(reconstructed.getDestination()).isEqualTo("cart.update");
        assertThat(reconstructed.getKey()).isEqualTo("cart-1");
        assertThat(reconstructed.getPayload()).isEqualTo("hello");
    }
}
