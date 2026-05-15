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
import java.util.Collections;
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

    /**
     * Prefix scan over the {@code username} column used to back the autocomplete
     * on the co-organizer invitation field (SCRUM-137 polish). The prefix is
     * normalised (trim + lowercase) and escaped before the {@code LIKE} so the
     * literal {@code _} that the username regex allows is not interpreted as
     * the SQL single-char wildcard. The validator on the caller side already
     * rejects anything outside {@code [a-z0-9._-]}, so {@code %} can never
     * reach this query.
     *
     * <p>The unique-constraint btree on {@code username} (V3 migration) is
     * adequate for the {@code LIKE 'prefix%'} pattern under PostgreSQL's
     * default collation since usernames are stored strictly lowercase ASCII —
     * EXPLAIN on a seeded DB confirms an index range scan. A dedicated
     * {@code text_pattern_ops} index is unnecessary at S8 traffic volumes.
     */
    public static List<User> searchByUsernamePrefix(String prefix, int limit, String excludeAuth0Id) {
        if (prefix == null || prefix.isBlank() || limit <= 0) {
            return Collections.emptyList();
        }
        String normalised = prefix.trim().toLowerCase();
        // Escape the only LIKE metacharacter that the username charset can produce
        // ('_'); '%' and '\' cannot appear because the resource-level validator
        // rejects anything outside [a-z0-9._-].
        String pattern = normalised.replace("_", "\\_") + "%";
        if (excludeAuth0Id == null) {
            return find("username LIKE ?1 ESCAPE '\\' ORDER BY username ASC", pattern)
                    .range(0, limit - 1)
                    .list();
        }
        return find(
                "username LIKE ?1 ESCAPE '\\' AND auth0Id <> ?2 ORDER BY username ASC",
                pattern,
                excludeAuth0Id
        ).range(0, limit - 1).list();
    }
}
