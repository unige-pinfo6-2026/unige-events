package ch.unige.events.dto.attendance;

import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.AttendanceStatus;
import ch.unige.events.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO renvoyé par les endpoints liés aux inscriptions :
 *   - POST /events/{id}/attend                      (l'attendance créée)
 *   - GET  /events/{id}/attendees (organisateur)    (liste des inscriptions à un event)
 *   - GET  /users/me/attendances                    (mes inscriptions)
 *
 * `displayName` et `avatarUrl` sont projetés depuis le User auquel se réfère
 * l'inscription. La route `/events/{id}/attendees` est restreinte au créateur
 * et aux co-organisateurs ACCEPTED, donc exposer ces champs y est sûr même
 * pour les profils `profilePublic = false` — c'est précisément ce qui permet
 * à la page stats d'afficher un nom plutôt qu'un UUID. Les autres routes
 * exposent uniquement les inscriptions de l'utilisateur courant, donc même
 * lecture safe.
 */
public record AttendanceDTO(
        Long id,
        UUID userId,
        Long eventId,
        AttendanceStatus status,
        LocalDateTime createdAt,
        String displayName,
        String avatarUrl
) {
    public static AttendanceDTO from(Attendance attendance, User user) {
        return new AttendanceDTO(
                attendance.id,
                attendance.userId,
                attendance.eventId,
                attendance.status,
                attendance.createdAt,
                user != null ? user.displayName : null,
                user != null ? user.avatarUrl : null
        );
    }
}
