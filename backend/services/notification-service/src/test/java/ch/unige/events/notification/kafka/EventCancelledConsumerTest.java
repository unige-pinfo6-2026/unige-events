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

/**
 * Pure Mockito unit tests for the EventCancelledConsumer — exercise the
 * branching logic (event-fetch failure, empty attendee list, creator skip,
 * delivery type filter) without needing Docker or a Kafka in-memory
 * connector.
 */
class EventCancelledConsumerTest {

    private NotificationService notificationService;
    private EventServiceClient eventClient;
    private EngagementServiceClient engagementClient;
    private EventCancelledConsumer consumer;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        eventClient = mock(EventServiceClient.class);
        engagementClient = mock(EngagementServiceClient.class);
        consumer = new EventCancelledConsumer(notificationService, eventClient, engagementClient);
    }

    private static EventDTO eventOf(long id, String title, UUID creatorId) {
        return new EventDTO(
                /* id */ id,
                /* title */ title,
                /* description */ "desc",
                /* location */ "loc",
                /* startDate */ java.time.LocalDateTime.now(),
                /* endDate */ java.time.LocalDateTime.now().plusHours(2),
                /* category */ null,
                /* faculty */ null,
                /* bannerUrl */ null,
                /* creatorId */ creatorId,
                /* status */ EventStatus.PUBLISHED,
                /* capacity */ null,
                /* allDay */ false,
                /* featured */ false,
                /* featuredAt */ null,
                /* attendingCount */ 0L,
                /* availableSpots */ null,
                /* waitlistedCount */ 0L,
                /* viewCount */ 0L,
                /* interestedCount */ 0L,
                /* websiteUrl */ null,
                /* contactEmail */ null,
                /* registrationDeadline */ null,
                /* tags */ List.of(),
                /* createdAt */ null,
                /* updatedAt */ null,
                /* parentEventId */ null,
                /* recurrenceRule */ null,
                /* coOrganizerOf */ null);
    }

    @Test
    void onCancelled_skipsNonCancelledType() {
        EventLifecycleEvent ev = EventLifecycleEvent.published(1L, UUID.randomUUID());
        consumer.onCancelled(ev);

        verify(eventClient, never()).getById(anyLong());
        verify(notificationService, never()).create(any(), any(), any(), any(), anyString());
    }

    @Test
    void onCancelled_eventResolveFails_skips() {
        when(eventClient.getById(42L)).thenReturn(null);
        consumer.onCancelled(EventLifecycleEvent.cancelled(42L, UUID.randomUUID()));

        verify(engagementClient, never()).getAttendeeIds(anyLong(), anyString());
        verify(notificationService, never()).create(any(), any(), any(), any(), anyString());
    }

    @Test
    void onCancelled_noAttendees_skips() {
        when(eventClient.getById(42L)).thenReturn(eventOf(42L, "Concert", UUID.randomUUID()));
        when(engagementClient.getAttendeeIds(42L, "ATTENDING")).thenReturn(List.of());

        consumer.onCancelled(EventLifecycleEvent.cancelled(42L, UUID.randomUUID()));

        verify(notificationService, never()).create(any(), any(), any(), any(), anyString());
    }

    @Test
    void onCancelled_fanoutToEachAttendee() {
        UUID creator = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(eventClient.getById(42L)).thenReturn(eventOf(42L, "Concert", creator));
        when(engagementClient.getAttendeeIds(42L, "ATTENDING")).thenReturn(List.of(a, b));

        consumer.onCancelled(EventLifecycleEvent.cancelled(42L, creator));

        ArgumentCaptor<UUID> userIdCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService, org.mockito.Mockito.times(2)).create(
                userIdCaptor.capture(),
                eq(NotificationType.EVENT_CANCELLED),
                eq(42L),
                eq(null),
                msgCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(List.of(a, b), userIdCaptor.getAllValues());
        org.junit.jupiter.api.Assertions.assertTrue(
                msgCaptor.getValue().contains("Concert"),
                "message must embed the event title");
    }

    @Test
    void onCancelled_skipsCreator_ifInAttendees() {
        UUID creator = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        when(eventClient.getById(42L)).thenReturn(eventOf(42L, "Concert", creator));
        when(engagementClient.getAttendeeIds(42L, "ATTENDING")).thenReturn(List.of(creator, other));

        consumer.onCancelled(EventLifecycleEvent.cancelled(42L, creator));

        // Only `other` receives a notification — creator is skipped.
        verify(notificationService, org.mockito.Mockito.times(1)).create(
                eq(other),
                eq(NotificationType.EVENT_CANCELLED),
                eq(42L),
                eq(null),
                anyString());
    }
}
