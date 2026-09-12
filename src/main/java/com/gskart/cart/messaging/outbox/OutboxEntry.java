package com.gskart.cart.messaging.outbox;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// One pending event in the Redis outbox. Payload's stored as a JSON string plus its class name so
// the relay can rebuild the real object before publishing.
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
