package com.gskart.cart.messaging;

// Blocks until the broker actually accepts the event (or throws) — the outbox relay relies on that
// to decide whether to retry, so don't make this async without fixing the relay too.
public interface DomainEventPublisher {
    /**
     * @throws EventPublishException if the broker did not accept the event.
     */
    void publish(DomainEvent event);
}
