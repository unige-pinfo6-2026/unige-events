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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * @QuarkusTest so quarkus-jacoco instruments the consumer bytecode for
 * Sonar coverage (standalone JUnit tests on @ApplicationScoped beans are
 * not counted by the Quarkus-only jacoco agent).
 *
 * <p><strong>java:S1612 suppressed</strong> on the lambda forms of
 * {@code () -> Notification.deleteAll()}: Sonar suggests collapsing them
 * to {@code Notification::deleteAll}, but that resolves the static method
 * at link time to {@code PanacheEntityBase.deleteAll()} (which throws
 * "implementation injection missing") instead of the Panache-enhanced
 * subclass implementation. The lambda defers the dispatch to runtime,
 * which picks up the enhanced static method correctly.
 */
@SuppressWarnings("java:S1612")
@QuarkusTest
class EventCancelledConsumerTest {

    @Inject EventCancelledConsumer consumer;
    @InjectMock @RestClient EventServiceClient eventClient;
    @InjectMock @RestClient EngagementServiceClient engagementClient;

    private static final long EVENT_ID = 42L;

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
    void onCancelled_skipsNonCancelledType_persistsNothing() {
        consumer.onCancelled(EventLifecycleEvent.published(EVENT_ID, UUID.randomUUID()));
        assertEquals(0, Notification.count());
    }

    @Test
    void onCancelled_eventResolveFails_persistsNothing() {
        when(eventClient.getById(EVENT_ID)).thenReturn(null);
        consumer.onCancelled(EventLifecycleEvent.cancelled(EVENT_ID, UUID.randomUUID()));
        assertEquals(0, Notification.count());
    }

    @Test
    void onCancelled_noAttendees_persistsNothing() {
        when(eventClient.getById(EVENT_ID)).thenReturn(eventOf("Concert", UUID.randomUUID()));
        when(engagementClient.getAttendeeIds(EVENT_ID, "ATTENDING")).thenReturn(List.of());
        consumer.onCancelled(EventLifecycleEvent.cancelled(EVENT_ID, UUID.randomUUID()));
        assertEquals(0, Notification.count());
    }

    @Test
    void onCancelled_nullAttendeeList_persistsNothing() {
        when(eventClient.getById(EVENT_ID)).thenReturn(eventOf("Concert", UUID.randomUUID()));
        when(engagementClient.getAttendeeIds(anyLong(), anyString())).thenReturn(null);
        consumer.onCancelled(EventLifecycleEvent.cancelled(EVENT_ID, UUID.randomUUID()));
        assertEquals(0, Notification.count());
    }

    @Test
    void onCancelled_fanoutToEachAttendee_andSkipsCreatorAndNulls() {
        UUID creator = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(eventClient.getById(EVENT_ID)).thenReturn(eventOf("Concert", creator));
        when(engagementClient.getAttendeeIds(EVENT_ID, "ATTENDING"))
                .thenReturn(java.util.Arrays.asList(a, creator, null, b));

        consumer.onCancelled(EventLifecycleEvent.cancelled(EVENT_ID, creator));

        // Only `a` and `b` get a notif — creator skipped (self), null skipped.
        List<Notification> rows = Notification.<Notification>list("eventId", EVENT_ID);
        assertEquals(2, rows.size());
        for (Notification n : rows) {
            assertEquals(NotificationType.EVENT_CANCELLED, n.type);
            assertEquals(EVENT_ID, n.eventId);
            assertNull(n.relatedUserId);
            assertNotNull(n.message);
            assertTrue(n.message.contains("Concert"), "message embeds the event title");
        }
        assertEquals(1L, Notification.count("userId = ?1 and eventId = ?2", a, EVENT_ID));
        assertEquals(1L, Notification.count("userId = ?1 and eventId = ?2", b, EVENT_ID));
        assertEquals(0L, Notification.count("userId = ?1 and eventId = ?2", creator, EVENT_ID));
    }
}
