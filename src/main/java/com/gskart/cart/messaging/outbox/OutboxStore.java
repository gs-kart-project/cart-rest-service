package com.gskart.cart.messaging.outbox;

import com.gskart.cart.messaging.DomainEvent;

import java.util.List;

/**
 * Transactional-outbox store. A cart mutation persists its outbound event here — into the same Redis
 * that holds the cart — instead of publishing to Kafka inline, so a failed broker can no longer
 * diverge Redis from Mongo (the pre-outbox bug). A relay drains it to the broker with at-least-once
 * delivery.
 *
 * <p>Entries are held in a time-ordered queue: each carries a "not before" time, so a failed publish
 * can be rescheduled for a later retry (exponential backoff) rather than hammered every tick. After a
 * bounded number of failed attempts the entry is dead-lettered instead of retried forever.
 */
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
