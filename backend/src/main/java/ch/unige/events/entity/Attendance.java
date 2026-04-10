package ch.unige.events.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
    name = "attendances",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_attendance_user_event",
        columnNames = {"user_id", "event_id"}
    )
)
public class Attendance extends PanacheEntity {

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(name = "event_id", nullable = false)
    public Long eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public AttendanceStatus status;

    @Column(updatable = false)
    public LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public static List<Attendance> findByEvent(Long eventId, int page, int size) {
        return find("eventId = ?1 order by id asc", eventId)
                .page(page, size)
                .list();
    }

    public static List<Attendance> findAllByUser(UUID userId) {
        return list("userId = ?1", userId);
    }

    /**
     * Bulk-counts attendances grouped by event for the given status.
     * Issues a single GROUP BY query instead of one query per event.
     *
     * @param eventIds list of event IDs to count for
     * @param status   attendance status to filter on
     * @param em       entity manager (injected by the calling service)
     * @return map of eventId → count; events with zero attendances are absent
     */
    public static Map<Long, Long> countGroupedByStatus(List<Long> eventIds, AttendanceStatus status, EntityManager em) {
        if (eventIds.isEmpty()) {
            return new HashMap<>();
        }
        List<Object[]> rows = em.createQuery(
                "SELECT a.eventId, COUNT(a) FROM Attendance a" +
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
