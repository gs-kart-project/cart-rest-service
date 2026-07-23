package com.gskart.cart.messaging.outbox;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A persisted, not-yet-published event in the Redis outbox. Stores the {@code DomainEvent}'s routing
 * fields plus the payload as JSON + its concrete type so the relay can faithfully reconstruct and
 * publish it. Serialized to/from JSON as the element stored in the Redis queue.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEntry {
    private String id;
    private String destination;
    private String key;
    private String eventType;
    private String payloadType;
    private String payload;
    private long createdAt;
    private int attempts;
}
