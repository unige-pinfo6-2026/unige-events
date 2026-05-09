package ch.unige.events.attendance.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only stub of the User entity. attendance-service needs auth0Id to
 * resolve the caller, plus displayName + avatarUrl to project AttendanceDTO
 * (needed for /events/{id}/attendees so organizers see names not UUIDs).
 * Replaced by REST client to user-service at PR 12.
 */
@Entity
@Table(name = "users")
public class UserStub extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "auth0_id", nullable = false, unique = true, updatable = false)
    public String auth0Id;

    @Column(name = "display_name")
    public String displayName;

    @Column(name = "avatar_url")
    public String avatarUrl;

    public static Optional<UserStub> findByAuth0Id(String auth0Id) {
        return find("auth0Id", auth0Id).firstResultOptional();
    }
}
