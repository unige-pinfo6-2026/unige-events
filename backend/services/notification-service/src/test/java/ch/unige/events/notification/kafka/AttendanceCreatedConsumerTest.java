package ch.unige.events.notification.kafka;

import ch.unige.events.notification.entity.Notification;
import ch.unige.events.notification.entity.NotificationType;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.kafka.events.AttendanceCreatedEvent;

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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import java.time.Month;

/**
 * Same pattern as {@link EventCancelledConsumerTest} (S1612 suppressed
 * for the deleteAll lambdas).
 */
@SuppressWarnings("java:S1612")
@QuarkusTest
class AttendanceCreatedConsumerTest {

    @Inject AttendanceCreatedConsumer consumer;
    @InjectMock @RestClient EventServiceClient eventClient;

    private static final long EVENT_ID = 242L;
    private static final long ATTENDANCE_ID = 7L;

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
                java.time.LocalDateTime.of(2025, Month.JANUARY, 1, 12, 0), java.time.LocalDateTime.of(2999, Month.JANUARY, 1, 0, 0).plusHours(2),
                null, null, null, creatorId, EventStatus.PUBLISHED, null, false, false, null,
                0L, null, 0L, 0L, 0L,
                null, null, null, List.of(), null, null, null, null, null);
    }

    @Test
    void onAttendanceCreated_notifiesCreator() {
        UUID creator = UUID.randomUUID();
        UUID attendee = UUID.randomUUID();
        when(eventClient.getById(EVENT_ID)).thenReturn(eventOf("Workshop", creator));

        consumer.onAttendanceCreated(AttendanceCreatedEvent.of(ATTENDANCE_ID, EVENT_ID, attendee));

        List<Notification> rows = Notification.<Notification>list("eventId", EVENT_ID);
        assertEquals(1, rows.size());
        Notification n = rows.get(0);
        assertEquals(creator, n.userId);
        assertEquals(NotificationType.NEW_ATTENDEE, n.type);
        assertEquals(attendee, n.relatedUserId);
        assertNotNull(n.message);
        assertTrue(n.message.contains("Workshop"));
    }

    @Test
    void onAttendanceCreated_creatorAttendsOwnEvent_skipped() {
        UUID creator = UUID.randomUUID();
        when(eventClient.getById(EVENT_ID)).thenReturn(eventOf("Workshop", creator));

        consumer.onAttendanceCreated(AttendanceCreatedEvent.of(ATTENDANCE_ID, EVENT_ID, /* userId */ creator));

        assertEquals(0, Notification.count("eventId", EVENT_ID));
    }

    @Test
    void onAttendanceCreated_eventResolveFails_skips() {
        when(eventClient.getById(EVENT_ID)).thenReturn(null);
        consumer.onAttendanceCreated(AttendanceCreatedEvent.of(ATTENDANCE_ID, EVENT_ID, UUID.randomUUID()));
        assertEquals(0, Notification.count("eventId", EVENT_ID));
    }

    @Test
    void onAttendanceCreated_nullCreatorIdInPayload_skips() {
        UUID attendee = UUID.randomUUID();
        when(eventClient.getById(EVENT_ID)).thenReturn(eventOf("Workshop", /* creatorId */ null));
        consumer.onAttendanceCreated(AttendanceCreatedEvent.of(ATTENDANCE_ID, EVENT_ID, attendee));
        assertEquals(0, Notification.count("eventId", EVENT_ID));
    }
}
