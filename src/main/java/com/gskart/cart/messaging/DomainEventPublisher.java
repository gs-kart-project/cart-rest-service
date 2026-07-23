package com.gskart.cart.messaging;

/**
 * Outbound messaging port. Publishes a {@link DomainEvent} to the broker synchronously — the call
 * returns only once the broker has accepted the event, and throws if it did not, so the caller (the
 * outbox relay) can decide whether to retry. A Kafka adapter implements this today; a different broker
 * adapter can be swapped in by config later without touching callers.
 */
public interface DomainEventPublisher {
    /**
     * @throws EventPublishException if the broker did not accept the event.
     */
    void publish(DomainEvent event);
}
