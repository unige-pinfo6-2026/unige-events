package ch.unige.events.notification.kafka;

import ch.unige.events.notification.entity.NotificationType;
import ch.unige.events.notification.service.NotificationService;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.kafka.events.AttendanceCreatedEvent;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.UUID;

/**
 * Consumes {@code attendances.created} (SCRUM-99 — new topic). Posts a
 * single {@link NotificationType#NEW_ATTENDEE} notification targeting the
 * event creator when a user signs up with effective status
 * {@code ATTENDING} (engagement-service filters at the source — Décision M).
 *
 * <p>Skips the case where the creator attends their own event (which would
 * be a useless self-notification).
 */
@ApplicationScoped
public class AttendanceCreatedConsumer {

    private static final String MESSAGE_TEMPLATE =
            "Un nouvel inscrit pour « %s ».";

    private final NotificationService notificationService;
    private final EventServiceClient eventClient;

    @Inject
    public AttendanceCreatedConsumer(NotificationService notificationService,
                                     @RestClient EventServiceClient eventClient) {
        this.notificationService = notificationService;
        this.eventClient = eventClient;
    }

    @Incoming("attendances-created")
    @Transactional
    public void onAttendanceCreated(AttendanceCreatedEvent ev) {
        EventDTO event = eventClient.getById(ev.eventId());
        if (event == null) {
            Log.warnf("[NOTIF_NEW_ATTENDEE_SKIPPED] event=%d unresolved (event-service unavailable or event hard-deleted)", ev.eventId());
            return;
        }
        UUID creatorId = event.creatorId();
        if (creatorId == null) {
            Log.warnf("[NOTIF_NEW_ATTENDEE_SKIPPED] event=%d has no creatorId in payload — skipping", ev.eventId());
            return;
        }
        if (creatorId.equals(ev.userId())) {
            // Creator attended their own event — no self-notification.
            return;
        }
        String message = MESSAGE_TEMPLATE.formatted(event.title());
        notificationService.create(creatorId, NotificationType.NEW_ATTENDEE, ev.eventId(), ev.userId(), message);
        Log.infof("[NOTIF_NEW_ATTENDEE] notified creator=%s for event=%d (attendee=%s)",
                creatorId, ev.eventId(), ev.userId());
    }
}
