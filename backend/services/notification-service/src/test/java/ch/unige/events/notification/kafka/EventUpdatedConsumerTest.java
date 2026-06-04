package ch.unige.events.notification.kafka;

import ch.unige.events.notification.entity.Notification;
import ch.unige.events.notification.entity.NotificationType;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.kafka.events.EventLifecycleEvent;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * @QuarkusTest so quarkus-jacoco instruments the consumer bytecode.
 * Cf. {@link EventCancelledConsumerTest} pour la note sur la suppression
 * S1612 (le method reference {@code Notification::deleteAll} se résout au
 * link-time vers la base class qui throw — le lambda defer au runtime).
 */
@SuppressWarnings("java:S1612")
@QuarkusTest
class EventUpdatedConsumerTest {

    @Inject EventUpdatedConsumer consumer;
    @InjectMock @RestClient EventServiceClient eventClient;
    @InjectMock @RestClient EngagementServiceClient engagementClient;

    private static final long EVENT_ID = 142L;

    @BeforeEach
    void truncate() {
        QuarkusTransaction.requiringNew().run(() -> Notification.deleteAll());
    }

    @AfterEach
    void cleanup() {
        QuarkusTransaction.requiringNew().run(() -> Notification.deleteAll());
    }

    private static EventDTO eventOf(String title, UUID creatorId) {
        return new EventDTO(EVENT_ID, title, "desc", "loc",
                java.time.LocalDateTime.of(2025, 1, 1, 12, 0), java.time.LocalDateTime.of(2999, 1, 1, 0, 0).plusHours(2),
                null, null, null, creatorId, EventStatus.PUBLISHED, null, false, false, null,
                0L, null, 0L, 0L, 0L,
                null, null, null, List.of(), null, null, null, null, null);
    }

    @Test
    void onUpdated_skipsNonUpdatedType_persistsNothing() {
        consumer.onUpdated(EventLifecycleEvent.cancelled(EVENT_ID, UUID.randomUUID()));
        assertEquals(0, Notification.count());
    }

    @Test
    void onUpdated_eventResolveFails_persistsNothing() {
        when(eventClient.getById(EVENT_ID)).thenReturn(null);
        consumer.onUpdated(EventLifecycleEvent.updated(EVENT_ID, UUID.randomUUID()));
        assertEquals(0, Notification.count());
    }

    @Test
    void onUpdated_noAttendees_persistsNothing() {
        when(eventClient.getById(EVENT_ID)).thenReturn(eventOf("Concert", UUID.randomUUID()));
        when(engagementClient.getAttendeeIds(EVENT_ID, "ATTENDING")).thenReturn(List.of());
        consumer.onUpdated(EventLifecycleEvent.updated(EVENT_ID, UUID.randomUUID()));
        assertEquals(0, Notification.count());
    }

    @Test
    void onUpdated_nullAttendeeList_persistsNothing() {
        when(eventClient.getById(EVENT_ID)).thenReturn(eventOf("Concert", UUID.randomUUID()));
        when(engagementClient.getAttendeeIds(anyLong(), anyString())).thenReturn(null);
        consumer.onUpdated(EventLifecycleEvent.updated(EVENT_ID, UUID.randomUUID()));
        assertEquals(0, Notification.count());
    }

    @Test
    void onUpdated_fanoutToEachAttendee_andSkipsCreatorAndNulls() {
        UUID creator = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(eventClient.getById(EVENT_ID)).thenReturn(eventOf("Concert", creator));
        when(engagementClient.getAttendeeIds(EVENT_ID, "ATTENDING"))
                .thenReturn(java.util.Arrays.asList(a, creator, null, b));

        consumer.onUpdated(EventLifecycleEvent.updated(EVENT_ID, creator));

        List<Notification> rows = Notification.<Notification>list("eventId", EVENT_ID);
        assertEquals(2, rows.size());
        for (Notification n : rows) {
            assertEquals(NotificationType.EVENT_UPDATED, n.type);
            assertTrue(n.message.contains("Concert"));
        }
    }
}
