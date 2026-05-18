package ch.unige.events.notification.kafka;

import ch.unige.events.notification.entity.NotificationType;
import ch.unige.events.notification.service.NotificationService;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.kafka.events.EventLifecycleEvent;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;
import java.util.UUID;

/**
 * Consumes {@code events.cancelled} (SCRUM-99). Fans out an
 * {@link NotificationType#EVENT_CANCELLED} notification to every
 * {@code ATTENDING} user of the cancelled event.
 *
 * <p>At-least-once semantics accepted (Décision D) — a redelivered message
 * produces duplicate notification rows ; the user might see a notification
 * twice but the system stays correct.
 */
@ApplicationScoped
public class EventCancelledConsumer {

    private static final String MESSAGE_TEMPLATE =
            "L'événement « %s » a été annulé.";

    private final NotificationService notificationService;
    private final EventServiceClient eventClient;
    private final EngagementServiceClient engagementClient;

    @Inject
    public EventCancelledConsumer(NotificationService notificationService,
                                  @RestClient EventServiceClient eventClient,
                                  @RestClient EngagementServiceClient engagementClient) {
        this.notificationService = notificationService;
        this.eventClient = eventClient;
        this.engagementClient = engagementClient;
    }

    @Incoming("events-cancelled")
    @Transactional
    public void onCancelled(EventLifecycleEvent ev) {
        // Safety filter — the channel topic already selects CANCELLED, but
        // a misconfigured broker or test wiring could deliver other types.
        if (ev.type() != EventLifecycleEvent.Type.CANCELLED) {
            return;
        }
        EventDTO event = eventClient.getById(ev.eventId());
        if (event == null) {
            Log.warnf("[NOTIF_EVENT_CANCELLED_SKIPPED] event=%d unresolved (event-service unavailable or event hard-deleted)", ev.eventId());
            return;
        }
        List<UUID> attendees = engagementClient.getAttendeeIds(ev.eventId(), "ATTENDING");
        if (attendees == null || attendees.isEmpty()) {
            return;
        }
        UUID creatorId = ev.creatorId();
        String message = MESSAGE_TEMPLATE.formatted(event.title());
        int sent = 0;
        for (UUID attendeeId : attendees) {
            if (attendeeId == null || attendeeId.equals(creatorId)) {
                // Skip the creator if they were attending their own event.
                continue;
            }
            notificationService.create(attendeeId, NotificationType.EVENT_CANCELLED, ev.eventId(), null, message);
            sent++;
        }
        Log.infof("[NOTIF_EVENT_CANCELLED] notified n=%d for event=%d", sent, ev.eventId());
    }
}
