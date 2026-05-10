package ch.unige.events.report.outbox;

import ch.unige.events.shared.kafka.events.EventBannedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Drains the {@code event_banned_outbox} table into the
 * {@code events.banned} Kafka topic every 10 seconds (Decision K, ADR-003).
 * At-least-once semantics: rows stay {@code published_at IS NULL} until
 * a successful Kafka send, and accumulate {@code attempts}+{@code last_error}
 * across retries.
 *
 * <p><b>Single-replica assumption</b> (ADR-001): {@code moderation-service}
 * runs at {@code replicas: 1}, so this scheduler does not need leader
 * election. If the deployment is ever scaled out, add Shedlock or
 * equivalent — concurrent pollers would otherwise double-publish.
 */
@ApplicationScoped
public class EventBannedOutboxPoller {

    private static final int BATCH_SIZE = 100;
    private static final int SEND_TIMEOUT_SECONDS = 5;

    private final Emitter<EventBannedEvent> emitter;
    private final ObjectMapper objectMapper;

    @Inject
    public EventBannedOutboxPoller(
            @Channel("events-banned") Emitter<EventBannedEvent> emitter,
            ObjectMapper objectMapper) {
        this.emitter = emitter;
        this.objectMapper = objectMapper;
    }

    @Scheduled(every = "10s")
    @Transactional
    public void publishPending() {
        List<EventBannedOutbox> pending = EventBannedOutbox
                .<EventBannedOutbox>find("publishedAt IS NULL ORDER BY id")
                .page(0, BATCH_SIZE)
                .list();
        for (EventBannedOutbox row : pending) {
            try {
                EventBannedEvent ev = objectMapper.readValue(row.payloadJson, EventBannedEvent.class);
                emitter.send(ev).toCompletableFuture().get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                row.publishedAt = Instant.now();
                row.lastError = null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                row.attempts++;
                row.lastError = "Interrupted while waiting for Kafka ack";
                Log.errorf(e,
                        "[KAFKA_OUTBOX_PUBLISH_FAIL_events.banned] outbox row %d interrupted (attempts=%d) — will retry next tick",
                        row.id, row.attempts);
            } catch (Exception e) {
                row.attempts++;
                row.lastError = e.toString();
                Log.errorf(e,
                        "[KAFKA_OUTBOX_PUBLISH_FAIL_events.banned] outbox row %d attempts=%d — will retry next tick",
                        row.id, row.attempts);
            }
        }
    }
}
