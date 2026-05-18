package ch.unige.events.notification.dto;

import ch.unige.events.notification.entity.Notification;
import ch.unige.events.notification.entity.NotificationType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Wire shape returned by {@code GET /api/users/me/notifications}.
 * Projection of {@link Notification} — same field semantics as the
 * OpenAPI {@code Notification} schema.
 */
public record NotificationDTO(
        Long id,
        UUID userId,
        NotificationType type,
        Long eventId,
        UUID relatedUserId,
        String message,
        boolean read,
        LocalDateTime createdAt,
        LocalDateTime readAt
) {

    public static NotificationDTO from(Notification n) {
        return new NotificationDTO(
                n.id,
                n.userId,
                n.type,
                n.eventId,
                n.relatedUserId,
                n.message,
                n.read,
                n.createdAt,
                n.readAt
        );
    }
}
