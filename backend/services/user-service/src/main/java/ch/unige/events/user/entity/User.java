package ch.unige.events.user.entity;

import ch.unige.events.shared.domain.enums.Faculty;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Owned by user-service. Carbon-copy of legacy
 * ch.unige.events.entity.User — same column layout, same finders, same
 * @Version optimistic locking.
 */
@Entity
@Table(name = "users")
public class User extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(nullable = false, unique = true, updatable = false)
    public String auth0Id;

    @Column(nullable = false, unique = true, updatable = false)
    public String email;

    /**
     * Public-facing identifier — used in {@code /profile/{username}} URLs and
     * exposed in the OpenAPI schema (SCRUM-169). Stored strictly lowercase, the
     * lookup {@link #findByUsername(String)} is case-insensitive. Generated
     * automatically at first signup via
     * {@code UserService.generateUsername(...)} and mutable through {@code
     * PATCH /users/me/username}. The pattern {@code ^[a-z0-9._-]{3,30}$} and
     * uniqueness are enforced at the DB level (V3 migration) ; this field
     * intentionally carries no Bean Validation annotations because every write
     * path produces a normalised value before persist.
     */
    @Column(nullable = false, unique = true, length = 30)
    public String username;

    public String displayName;
    public String firstName;
    public String lastName;

    @Enumerated(EnumType.STRING)
    public Faculty faculty;

    /**
     * Free-text study level until {@code StudyLevel} enum is introduced
     * (deferred to S9+). Expected canonical values: {@code LICENCE},
     * {@code MASTER}, {@code PHD}, {@code OTHER}.
     */
    public String studyLevel;

    @Column(columnDefinition = "TEXT")
    public String bio;

    @ElementCollection(fetch = FetchType.EAGER)
    public List<String> interests;

    public String avatarUrl;
    public String bannerUrl;

    @Column(nullable = false)
    public boolean profilePublic = false;

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    @Version
    @Column(nullable = false)
    public long version = 0L;

    @Column(unique = true)
    public UUID calendarToken;

    public static Optional<User> findByAuth0Id(String auth0Id) {
        return find("auth0Id", auth0Id).firstResultOptional();
    }

    public static Optional<User> findByEmail(String email) {
        return find("email", email).firstResultOptional();
    }

    public static Optional<User> findByCalendarToken(UUID calendarToken) {
        return find("calendarToken", calendarToken).firstResultOptional();
    }

    /**
     * Case-insensitive lookup (SCRUM-169). The input is lowered before the
     * SELECT — usernames are stored strictly lowercase so a column-level
     * {@code LOWER(username)} would be redundant, but normalising the
     * argument keeps {@code GET /by-username/Jean.Dupont} matching the
     * persisted {@code jean.dupont}.
     */
    public static Optional<User> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return find("username", username.toLowerCase()).firstResultOptional();
    }
}
