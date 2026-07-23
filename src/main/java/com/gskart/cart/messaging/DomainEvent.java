package com.gskart.cart.messaging;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Transport-agnostic outbound event. Carries the destination (logical topic), a partition/ordering
 * key, a semantic event type for logging, and the payload object. The Kafka adapter turns this into a
 * {@code ProducerRecord}; a different broker adapter would map it to that broker's message later.
 */
@Getter
@Builder
@ToString
public class DomainEvent {
    /** Logical destination (Kafka topic today). */
    private final String destination;
    /** Ordering/partition key (cart id today). */
    private final String key;
    /** Semantic label for logging/tracing, e.g. {@code CART_UPDATE}. */
    private final String eventType;
    /** The event body — a {@code Cart} or {@code OrderRequest}; the adapter serializes it. */
    private final Object payload;
}
