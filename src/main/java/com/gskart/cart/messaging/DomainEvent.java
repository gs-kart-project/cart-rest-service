package com.gskart.cart.messaging;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

// Keeps event publishing broker-agnostic — Kafka turns this into a ProducerRecord today, but a
// different broker adapter could reuse it later without touching callers.
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
