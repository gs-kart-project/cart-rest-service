package com.gskart.cart.messaging.outbox;

import com.gskart.cart.messaging.DomainEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

// Runs on a schedule, grabs whatever's due from the outbox, and publishes it. A failed publish
// backs off exponentially and retries; once we're out of attempts we dead-letter it instead of
// retrying forever. That's safe because consumers are idempotent — an occasional duplicate
// delivery doesn't hurt.
@Slf4j
@Component
public class OutboxRelay {

    private static final int BATCH_SIZE = 100;

    private final OutboxStore outboxStore;
    private final DomainEventPublisher publisher;

    private final int maxAttempts;
    private final long initialBackoffMillis;
    private final long maxBackoffMillis;
    private final double backoffMultiplier;

    public OutboxRelay(OutboxStore outboxStore,
                       DomainEventPublisher publisher,
                       @Value("${gskart.outbox.relay.max-attempts:5}") int maxAttempts,
                       @Value("${gskart.outbox.relay.backoff.initial-ms:10000}") long initialBackoffMillis,
                       @Value("${gskart.outbox.relay.backoff.max-ms:120000}") long maxBackoffMillis,
                       @Value("${gskart.outbox.relay.backoff.multiplier:2}") double backoffMultiplier) {
        this.outboxStore = outboxStore;
        this.publisher = publisher;
        this.maxAttempts = maxAttempts;
        this.initialBackoffMillis = initialBackoffMillis;
        this.maxBackoffMillis = maxBackoffMillis;
        this.backoffMultiplier = backoffMultiplier;
    }

    @Scheduled(fixedDelayString = "${gskart.outbox.relay.fixed-delay-ms:2000}")
    public void drain() {
        List<OutboxEntry> batch = outboxStore.claimDue(BATCH_SIZE);
        for (OutboxEntry entry : batch) {
            try {
                publisher.publish(outboxStore.toDomainEvent(entry));
                outboxStore.markPublished(entry);
            } catch (Exception e) {
                handleFailure(entry, e);
            }
        }
    }

    private void handleFailure(OutboxEntry entry, Exception e) {
        int attemptsMade = entry.getAttempts() + 1;
        if (attemptsMade >= maxAttempts) {
            log.error("Outbox entry {} ({} -> {}) failed {} times; dead-lettering.",
                    entry.getId(), entry.getEventType(), entry.getDestination(), attemptsMade, e);
            outboxStore.deadLetter(entry);
            return;
        }
        long delay = backoffMillis(attemptsMade);
        log.warn("Outbox entry {} ({} -> {}) failed (attempt {}/{}); retrying in {} ms.",
                entry.getId(), entry.getEventType(), entry.getDestination(), attemptsMade, maxAttempts, delay, e);
        outboxStore.reschedule(entry, delay);
    }

    /** Exponential backoff for the n-th failed attempt (1-based), capped at maxBackoffMillis. */
    private long backoffMillis(int attemptsMade) {
        double delay = initialBackoffMillis * Math.pow(backoffMultiplier, attemptsMade - 1);
        return (long) Math.min(delay, maxBackoffMillis);
    }
}
