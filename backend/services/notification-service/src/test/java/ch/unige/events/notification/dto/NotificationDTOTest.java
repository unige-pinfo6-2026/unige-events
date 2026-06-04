package ch.unige.events.notification.dto;

import ch.unige.events.notification.entity.Notification;
import ch.unige.events.notification.entity.NotificationType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NotificationDTOTest {

    @Test
    void from_copiesEveryField() {
        Notification n = new Notification();
        n.id = 42L;
        n.userId = UUID.randomUUID();
        n.type = NotificationType.NEW_ATTENDEE;
        n.eventId = 7L;
        n.relatedUserId = UUID.randomUUID();
        n.message = "hello";
        n.read = true;
        n.createdAt = LocalDateTime.of(2026, Month.JANUARY, 1, 12, 0);
        n.readAt = LocalDateTime.of(2026, Month.JANUARY, 2, 12, 0);

        NotificationDTO dto = NotificationDTO.from(n);
        assertEquals(n.id, dto.id());
        assertEquals(n.userId, dto.userId());
        assertEquals(n.type, dto.type());
        assertEquals(n.eventId, dto.eventId());
        assertEquals(n.relatedUserId, dto.relatedUserId());
        assertEquals(n.message, dto.message());
        assertEquals(n.read, dto.read());
        assertEquals(n.createdAt, dto.createdAt());
        assertEquals(n.readAt, dto.readAt());
    }

    @Test
    void from_passesNullablesThrough() {
        Notification n = new Notification();
        n.id = 1L;
        n.userId = UUID.randomUUID();
        n.type = NotificationType.EVENT_CANCELLED;
        n.eventId = null;
        n.relatedUserId = null;
        n.message = "x";
        n.read = false;
        n.createdAt = LocalDateTime.of(2025, Month.JANUARY, 1, 12, 0);
        n.readAt = null;

        NotificationDTO dto = NotificationDTO.from(n);
        assertNull(dto.eventId());
        assertNull(dto.relatedUserId());
        assertNull(dto.readAt());
    }
}
