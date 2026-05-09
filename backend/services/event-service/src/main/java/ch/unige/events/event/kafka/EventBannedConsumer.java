package ch.unige.events.event.kafka;

import ch.unige.events.event.entity.Event;
import ch.unige.events.event.entity.EventStatus;
import ch.unige.events.shared.kafka.events.EventBannedEvent;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Incoming;

/**
 * Consumes {@code events.banned} (KAFKA-002 BLOCKER pair). Applies
 * {@code event.status = BANNED} locally so the source-of-truth row in
 * the events table reflects the moderation decision. Idempotent — a
 * replay of the same message is a no-op once status is BANNED, and a
 * missing event row is silently skipped (the event was deleted between
 * publish and consume).
 */
@ApplicationScoped
public class EventBannedConsumer {

    @Incoming("events-banned")
    @Transactional
    public void onBanned(EventBannedEvent ev) {
        Event event = Event.<Event>findByIdOptional(ev.eventId()).orElse(null);
        if (event == null) {
            Log.infof("events.banned: event %d not found locally — skipping (deleted before consume?)", ev.eventId());
            return;
        }
        if (event.status == EventStatus.BANNED) {
            // Idempotent — re-delivery shouldn't refire downstream effects.
            return;
        }
        event.status = EventStatus.BANNED;
        Log.infof("events.banned consumed for event %d (bannedBy=%s, reason=%s)",
                ev.eventId(), ev.bannedBy(), ev.reason());
    }
}
