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
 * Consumes {@code events.updated} (SCRUM-99 — new topic). Fans out an
 * {@link NotificationType#EVENT_UPDATED} notification to every
 * {@code ATTENDING} user of the updated event.
 *
 * <p>At-least-once semantics accepted (Décision D). Two consecutive updates
 * legitimately produce two notifications — that's the expected UX.
 */
@ApplicationScoped
public class EventUpdatedConsumer {

    private static final String MESSAGE_TEMPLATE =
            "L'événement « %s » a été modifié.";

    private final NotificationService notificationService;
    private final EventServiceClient eventClient;
    private final EngagementServiceClient engagementClient;

    @Inject
    public EventUpdatedConsumer(NotificationService notificationService,
                                @RestClient EventServiceClient eventClient,
                                @RestClient EngagementServiceClient engagementClient) {
        this.notificationService = notificationService;
        this.eventClient = eventClient;
        this.engagementClient = engagementClient;
    }

    @Incoming("events-updated")
    @Transactional
    public void onUpdated(EventLifecycleEvent ev) {
        if (ev.type() != EventLifecycleEvent.Type.UPDATED) {
            return;
        }
        EventDTO event = eventClient.getById(ev.eventId());
        if (event == null) {
            Log.warnf("[NOTIF_EVENT_UPDATED_SKIPPED] event=%d unresolved (event-service unavailable or event hard-deleted)", ev.eventId());
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
                continue;
            }
            notificationService.create(attendeeId, NotificationType.EVENT_UPDATED, ev.eventId(), null, message);
            sent++;
        }
        Log.infof("[NOTIF_EVENT_UPDATED] notified n=%d for event=%d", sent, ev.eventId());
    }
}
