package ch.unige.events.calendar.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.Optional;
import java.util.UUID;

/**
 * Writable stub of the User entity. calendar-service needs to read
 * {@code calendar_token} (resolve token → user when serving the ICS feed)
 * AND write it (token rotation on first access + on regenerate). Full
 * User entity lives in user-service (PR 12) — at that point this stub is
 * replaced by REST clients
 * ({@code GET /internal/users/by-calendar-token/{token}} +
 * {@code POST /users/{id}/calendar-token/regenerate}).
 *
 * <p>{@code @Version} mirrored from the legacy User entity so optimistic
 * locking continues to fire when calendar-service updates the token. The
 * remaining columns (email, displayName, ...) are intentionally not
 * declared — Hibernate validate only checks columns we touch, and we
 * never INSERT a User from this service.
 */
@Entity
@Table(name = "users")
public class UserStub extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "auth0_id", nullable = false, unique = true, updatable = false)
    public String auth0Id;

    @Column(name = "calendar_token", unique = true)
    public UUID calendarToken;

    @Version
    @Column(nullable = false)
    public long version = 0L;

    public static Optional<UserStub> findByAuth0Id(String auth0Id) {
        return find("auth0Id", auth0Id).firstResultOptional();
    }

    public static Optional<UserStub> findByCalendarToken(UUID token) {
        return find("calendarToken", token).firstResultOptional();
    }
}
