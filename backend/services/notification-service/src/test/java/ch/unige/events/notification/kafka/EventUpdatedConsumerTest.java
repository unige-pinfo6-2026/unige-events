package ch.unige.events.notification.kafka;

import ch.unige.events.notification.entity.NotificationType;
import ch.unige.events.notification.service.NotificationService;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.kafka.events.EventLifecycleEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventUpdatedConsumerTest {

    private NotificationService notificationService;
    private EventServiceClient eventClient;
    private EngagementServiceClient engagementClient;
    private EventUpdatedConsumer consumer;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        eventClient = mock(EventServiceClient.class);
        engagementClient = mock(EngagementServiceClient.class);
        consumer = new EventUpdatedConsumer(notificationService, eventClient, engagementClient);
    }

    private static EventDTO eventOf(long id, String title, UUID creatorId) {
        return new EventDTO(id, title, "desc", "loc",
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now().plusHours(2),
                null, null, null, creatorId, EventStatus.PUBLISHED, null, false, false, null,
                0L, null, 0L, 0L, 0L,
                null, null, null, List.of(), null, null, null, null, null);
    }

    @Test
    void onUpdated_skipsNonUpdatedType() {
        consumer.onUpdated(EventLifecycleEvent.cancelled(1L, UUID.randomUUID()));

        verify(eventClient, never()).getById(anyLong());
        verify(notificationService, never()).create(any(), any(), any(), any(), anyString());
    }

    @Test
    void onUpdated_eventResolveFails_skips() {
        when(eventClient.getById(42L)).thenReturn(null);
        consumer.onUpdated(EventLifecycleEvent.updated(42L, UUID.randomUUID()));

        verify(engagementClient, never()).getAttendeeIds(anyLong(), anyString());
        verify(notificationService, never()).create(any(), any(), any(), any(), anyString());
    }

    @Test
    void onUpdated_noAttendees_skips() {
        when(eventClient.getById(42L)).thenReturn(eventOf(42L, "Concert", UUID.randomUUID()));
        when(engagementClient.getAttendeeIds(42L, "ATTENDING")).thenReturn(List.of());

        consumer.onUpdated(EventLifecycleEvent.updated(42L, UUID.randomUUID()));

        verify(notificationService, never()).create(any(), any(), any(), any(), anyString());
    }

    @Test
    void onUpdated_fanoutToEachAttendee() {
        UUID creator = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(eventClient.getById(42L)).thenReturn(eventOf(42L, "Concert", creator));
        when(engagementClient.getAttendeeIds(42L, "ATTENDING")).thenReturn(List.of(a, b));

        consumer.onUpdated(EventLifecycleEvent.updated(42L, creator));

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService, org.mockito.Mockito.times(2)).create(
                any(UUID.class), eq(NotificationType.EVENT_UPDATED), eq(42L), eq(null), msgCaptor.capture());
        org.junit.jupiter.api.Assertions.assertTrue(
                msgCaptor.getValue().contains("Concert"),
                "message must embed the event title");
    }

    @Test
    void onUpdated_skipsCreator_ifInAttendees() {
        UUID creator = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        when(eventClient.getById(42L)).thenReturn(eventOf(42L, "Concert", creator));
        when(engagementClient.getAttendeeIds(42L, "ATTENDING")).thenReturn(List.of(creator, other));

        consumer.onUpdated(EventLifecycleEvent.updated(42L, creator));

        verify(notificationService, org.mockito.Mockito.times(1)).create(
                eq(other), eq(NotificationType.EVENT_UPDATED), eq(42L), eq(null), anyString());
    }
}
