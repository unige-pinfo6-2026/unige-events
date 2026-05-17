package ch.unige.events.notification.kafka;

import ch.unige.events.notification.entity.NotificationType;
import ch.unige.events.notification.service.NotificationService;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.kafka.events.AttendanceCreatedEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttendanceCreatedConsumerTest {

    private NotificationService notificationService;
    private EventServiceClient eventClient;
    private AttendanceCreatedConsumer consumer;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        eventClient = mock(EventServiceClient.class);
        consumer = new AttendanceCreatedConsumer(notificationService, eventClient);
    }

    private static EventDTO eventOf(long id, String title, UUID creatorId) {
        return new EventDTO(id, title, "desc", "loc",
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now().plusHours(2),
                null, null, null, creatorId, EventStatus.PUBLISHED, null, false, false, null,
                0L, null, 0L, 0L, 0L,
                null, null, null, List.of(), null, null, null, null, null);
    }

    @Test
    void onAttendanceCreated_notifiesCreator() {
        UUID creator = UUID.randomUUID();
        UUID attendee = UUID.randomUUID();
        when(eventClient.getById(42L)).thenReturn(eventOf(42L, "Workshop", creator));

        consumer.onAttendanceCreated(AttendanceCreatedEvent.of(1L, 42L, attendee));

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).create(
                eq(creator),
                eq(NotificationType.NEW_ATTENDEE),
                eq(42L),
                eq(attendee),
                msgCaptor.capture());
        org.junit.jupiter.api.Assertions.assertTrue(
                msgCaptor.getValue().contains("Workshop"),
                "message must embed the event title");
    }

    @Test
    void onAttendanceCreated_creatorAttendsOwnEvent_skipped() {
        UUID creator = UUID.randomUUID();
        when(eventClient.getById(42L)).thenReturn(eventOf(42L, "Workshop", creator));

        consumer.onAttendanceCreated(AttendanceCreatedEvent.of(1L, 42L, /* userId */ creator));

        verify(notificationService, never()).create(any(), any(), any(), any(), anyString());
    }

    @Test
    void onAttendanceCreated_eventResolveFails_skips() {
        when(eventClient.getById(42L)).thenReturn(null);

        consumer.onAttendanceCreated(AttendanceCreatedEvent.of(1L, 42L, UUID.randomUUID()));

        verify(notificationService, never()).create(any(), any(), any(), any(), anyString());
    }

    @Test
    void onAttendanceCreated_nullCreatorIdInPayload_skips() {
        UUID attendee = UUID.randomUUID();
        when(eventClient.getById(42L)).thenReturn(eventOf(42L, "Workshop", /* creatorId */ null));

        consumer.onAttendanceCreated(AttendanceCreatedEvent.of(1L, 42L, attendee));

        verify(notificationService, never()).create(any(), any(), any(), any(), anyString());
    }
}
