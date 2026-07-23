package com.gskart.cart.messaging.outbox;

import com.gskart.cart.messaging.DomainEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Redis-backed {@link OutboxStore}. Pending entries live in a sorted set {@code cart:outbox:pending}
 * scored by the epoch-millis time they next become eligible to publish; {@link #claimDue} reads only
 * the entries whose time has arrived. A successful publish removes the entry; a failed one is either
 * re-added with a later score (backoff) or moved to the {@code cart:outbox:dead} list once retries are
 * exhausted. Entries stay in the sorted set until acked, so a crashed relay simply finds them still
 * there on the next run — no separate recovery step is needed.
 *
 * <p>The cart itself lives in this same Redis, so persisting the event here (instead of publishing to
 * Kafka inline) is what closes the pre-outbox divergence bug: a broker outage can no longer leave
 * Redis and Mongo out of step.
 */
@Slf4j
@Component
public class RedisOutboxStore implements OutboxStore {

    static final String PENDING_KEY = "cart:outbox:pending";
    static final String DEAD_KEY = "cart:outbox:dead";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisOutboxStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(DomainEvent event) {
        Object payload = event.getPayload();
        OutboxEntry entry = new OutboxEntry(
                UUID.randomUUID().toString(),
                event.getDestination(),
                event.getKey(),
                event.getEventType(),
                payload == null ? null : payload.getClass().getName(),
                writeJson(payload),
                nowMillis(),
                0);
        // Score = now: eligible immediately.
        redisTemplate.opsForZSet().add(PENDING_KEY, writeJson(entry), nowMillis());
        log.debug("Appended {} event (key {}) to outbox as entry {}.",
                entry.getEventType(), entry.getKey(), entry.getId());
    }

    @Override
    public List<OutboxEntry> claimDue(int batchSize) {
        Set<String> due = redisTemplate.opsForZSet()
                .rangeByScore(PENDING_KEY, 0, nowMillis(), 0, batchSize);
        List<OutboxEntry> claimed = new ArrayList<>();
        if (due != null) {
            for (String json : due) {
                claimed.add(readEntry(json));
            }
        }
        return claimed;
    }

    @Override
    public void markPublished(OutboxEntry entry) {
        redisTemplate.opsForZSet().remove(PENDING_KEY, writeJson(entry));
    }

    @Override
    public void reschedule(OutboxEntry entry, long delayMillis) {
        ZSetOperations<String, String> zSet = redisTemplate.opsForZSet();
        zSet.remove(PENDING_KEY, writeJson(entry));
        entry.setAttempts(entry.getAttempts() + 1);
        zSet.add(PENDING_KEY, writeJson(entry), nowMillis() + delayMillis);
    }

    @Override
    public void deadLetter(OutboxEntry entry) {
        redisTemplate.opsForZSet().remove(PENDING_KEY, writeJson(entry));
        redisTemplate.opsForList().rightPush(DEAD_KEY, writeJson(entry));
    }

    @Override
    public DomainEvent toDomainEvent(OutboxEntry entry) {
        return DomainEvent.builder()
                .destination(entry.getDestination())
                .key(entry.getKey())
                .eventType(entry.getEventType())
                .payload(readPayload(entry))
                .build();
    }

    private long nowMillis() {
        return OffsetDateTime.now(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    private Object readPayload(OutboxEntry entry) {
        if (entry.getPayload() == null || entry.getPayloadType() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(entry.getPayload(), Class.forName(entry.getPayloadType()));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Unknown outbox payload type " + entry.getPayloadType(), e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize outbox value", e);
        }
    }

    private OutboxEntry readEntry(String json) {
        try {
            return objectMapper.readValue(json, OutboxEntry.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to deserialize outbox entry", e);
        }
    }
}
