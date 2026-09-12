package com.gskart.cart.messaging.outbox;

import com.gskart.cart.messaging.DomainEvent;

import java.util.List;

// Outbox for cart mutations — an event gets written here (same Redis as the cart) instead of going
// straight to Kafka, so a broker outage can't leave Redis and Mongo out of sync. A relay drains it
// with at-least-once delivery. Entries carry a "not before" time so a failed publish can back off
// and retry instead of hammering every tick; after enough failed attempts we give up and
// dead-letter it.
public interface OutboxStore {
    /** Persist an event as a pending outbox entry, eligible to publish immediately. */
    void append(DomainEvent event);

    /** Return up to {@code batchSize} entries whose retry time has arrived (oldest-due first). */
    List<OutboxEntry> claimDue(int batchSize);

    /** Mark a successfully published entry done (removes it from the queue). */
    void markPublished(OutboxEntry entry);

    /** Reschedule a failed entry for another attempt {@code delayMillis} from now (attempts + 1). */
    void reschedule(OutboxEntry entry, long delayMillis);

    /** Give up on an entry after exhausting retries: move it to the dead-letter list. */
    void deadLetter(OutboxEntry entry);

    /** Reconstruct the publishable {@link DomainEvent} an entry stands for. */
    DomainEvent toDomainEvent(OutboxEntry entry);
}
