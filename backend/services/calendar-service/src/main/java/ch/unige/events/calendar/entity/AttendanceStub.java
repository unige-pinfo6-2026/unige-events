package ch.unige.events.calendar.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.List;
import java.util.UUID;

/**
 * Read-only stub of the Attendance entity. calendar-service includes
 * every event the user has signed up for (any status) in the ICS feed.
 * Full Attendance entity lives in attendance-service (PR 8) — at that
 * point this stub is replaced by a REST client.
 *
 * <p>The {@code status} column is intentionally not declared : the
 * legacy ICS feed includes WAITLISTED entries too, so we never filter
 * on status here.
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

    public static List<AttendanceStub> findAllByUser(UUID userId) {
        return list("userId = ?1", userId);
    }
}
