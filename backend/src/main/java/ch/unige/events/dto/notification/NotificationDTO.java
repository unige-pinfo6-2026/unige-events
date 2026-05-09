package ch.unige.events.dto.notification;

import ch.unige.events.entity.Notification;
import ch.unige.events.entity.NotificationType;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationDTO(
        Long id,
        UUID userId,
        NotificationType type,
        String message,
        Long eventId,
        boolean read,
        LocalDateTime createdAt
) {
    public static NotificationDTO from(Notification n) {
        return new NotificationDTO(n.id, n.userId, n.type, n.message, n.eventId, n.read, n.createdAt);
    }
}
