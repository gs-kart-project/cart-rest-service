package com.gskart.cart.messaging.outbox;

import com.gskart.cart.messaging.DomainEvent;
import com.gskart.cart.messaging.DomainEventPublisher;
import com.gskart.cart.messaging.EventPublishException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxStore outboxStore;
    @Mock
    private DomainEventPublisher publisher;

    // max 5 attempts, backoff 10s base x2, cap 120s.
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(outboxStore, publisher, 5, 10_000L, 120_000L, 2.0);
    }

    private OutboxEntry entry(String id, int attempts) {
        return new OutboxEntry(id, "cart.update", "cart-1", "CART_UPDATE",
                "java.lang.String", "\"hello\"", 0L, attempts);
    }

    @Test
    void drain_publishesAndAcksEachDueEntry() {
        OutboxEntry e1 = entry("id-1", 0);
        OutboxEntry e2 = entry("id-2", 0);
        when(outboxStore.claimDue(anyInt())).thenReturn(List.of(e1, e2));
        when(outboxStore.toDomainEvent(any())).thenReturn(mock(DomainEvent.class));

        relay.drain();

        verify(publisher, times(2)).publish(any());
        verify(outboxStore).markPublished(e1);
        verify(outboxStore).markPublished(e2);
        verify(outboxStore, never()).reschedule(any(), anyLong());
        verify(outboxStore, never()).deadLetter(any());
    }

    @Test
    void drain_reschedulesWithFirstBackoff_onFirstFailure() {
        OutboxEntry e1 = entry("id-1", 0);
        when(outboxStore.claimDue(anyInt())).thenReturn(List.of(e1));
        when(outboxStore.toDomainEvent(e1)).thenReturn(mock(DomainEvent.class));
        doThrow(new EventPublishException("broker down", new RuntimeException()))
                .when(publisher).publish(any());

        relay.drain();

        // 1st failure -> initial backoff of 10s.
        verify(outboxStore).reschedule(e1, 10_000L);
        verify(outboxStore, never()).deadLetter(any());
    }

    @Test
    void drain_appliesExponentialBackoff_onSubsequentFailure() {
        OutboxEntry e1 = entry("id-1", 2); // two failures already; this is the 3rd
        when(outboxStore.claimDue(anyInt())).thenReturn(List.of(e1));
        when(outboxStore.toDomainEvent(e1)).thenReturn(mock(DomainEvent.class));
        doThrow(new EventPublishException("broker down", new RuntimeException()))
                .when(publisher).publish(any());

        relay.drain();

        // 3rd failure -> 10s * 2^2 = 40s.
        verify(outboxStore).reschedule(e1, 40_000L);
    }

    @Test
    void drain_capsBackoffAtMax() {
        OutboxRelay lowCap = new OutboxRelay(outboxStore, publisher, 10, 10_000L, 30_000L, 2.0);
        OutboxEntry e1 = entry("id-1", 4); // 5th failure -> 10s*2^4=160s, capped to 30s
        when(outboxStore.claimDue(anyInt())).thenReturn(List.of(e1));
        when(outboxStore.toDomainEvent(e1)).thenReturn(mock(DomainEvent.class));
        doThrow(new EventPublishException("broker down", new RuntimeException()))
                .when(publisher).publish(any());

        lowCap.drain();

        verify(outboxStore).reschedule(e1, 30_000L);
    }

    @Test
    void drain_deadLettersEntry_onceMaxAttemptsReached() {
        OutboxEntry e1 = entry("id-1", 4); // 4 failures already; this is the 5th and last
        when(outboxStore.claimDue(anyInt())).thenReturn(List.of(e1));
        when(outboxStore.toDomainEvent(e1)).thenReturn(mock(DomainEvent.class));
        doThrow(new EventPublishException("broker down", new RuntimeException()))
                .when(publisher).publish(any());

        relay.drain();

        verify(outboxStore).deadLetter(e1);
        verify(outboxStore, never()).reschedule(eq(e1), anyLong());
    }

    @Test
    void drain_doesNothing_whenNothingDue() {
        when(outboxStore.claimDue(anyInt())).thenReturn(List.of());

        relay.drain();

        verifyNoInteractions(publisher);
    }
}
