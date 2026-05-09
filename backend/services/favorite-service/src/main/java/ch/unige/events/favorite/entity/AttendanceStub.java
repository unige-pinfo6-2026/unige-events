package ch.unige.events.favorite.entity;

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
 * Read-only stub of the Attendance entity. favorite-service needs the
 * attending / waitlisted counts grouped by event to populate
 * {@link ch.unige.events.favorite.dto.EventDTO} fields {@code attendingCount},
 * {@code waitlistedCount} and {@code availableSpots}. Full attendance
 * lifecycle lives in attendance-service (PR 8) — at that point this stub
 * is replaced by a REST client.
 *
 * <p>The unique constraint declaration matches the legacy monolith so
 * Hibernate validate accepts the existing schema; we never write to this
 * table from favorite-service.
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

    /**
     * Bulk-counts attendances grouped by event for the given status. Single
     * GROUP BY query — same semantics as legacy
     * {@code Attendance.countGroupedByStatus}.
     */
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
