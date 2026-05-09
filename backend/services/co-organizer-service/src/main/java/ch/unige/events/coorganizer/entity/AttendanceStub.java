package ch.unige.events.coorganizer.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only stub of the Attendance entity. co-organizer-service uses it
 * to bulk-count ATTENDING / WAITLISTED for the EventDTO projection in
 * {@code GET /users/me/co-organizer-invitations}. Replaced by REST client
 * to attendance-service at PR 8.
 */
@Entity
@Table(
    name = "attendances",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_attendance_user_event",
        columnNames = {"user_id", "event_id"}
    )
)
public class AttendanceStub extends PanacheEntity {

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(name = "event_id", nullable = false)
    public Long eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public AttendanceStatus status;

    public static Map<Long, Long> countGroupedByStatus(List<Long> eventIds,
                                                      AttendanceStatus status,
                                                      EntityManager em) {
        if (eventIds.isEmpty()) {
            return new HashMap<>();
        }
        List<Object[]> rows = em.createQuery(
                "SELECT a.eventId, COUNT(a) FROM AttendanceStub a" +
                " WHERE a.eventId IN :ids AND a.status = :status" +
                " GROUP BY a.eventId",
                Object[].class)
                .setParameter("ids", eventIds)
                .setParameter("status", status)
                .getResultList();
        Map<Long, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put((Long) row[0], (Long) row[1]);
        }
        return result;
    }
}
