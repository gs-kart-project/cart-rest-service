package com.gskart.cart.messaging;

/**
 * Raised by a {@link DomainEventPublisher} when the broker did not accept an event. The outbox
 * relay catches this and leaves the entry pending for the next tick.
 */
public class EventPublishException extends RuntimeException {
    public EventPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
