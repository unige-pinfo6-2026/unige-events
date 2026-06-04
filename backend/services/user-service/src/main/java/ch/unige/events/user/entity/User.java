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
import java.time.ZoneId;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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

    /**
     * Auth0 roles mirrored from the JWT claim configured by
     * {@code OIDC_ROLE_NAMESPACE}. Synced by
     * {@code UserService.getOrCreateUser(...)} on every GET /users/me
     * hit — the frontend bootstraps from /me on every page load, so any
     * role change in Auth0 is picked up at the next session refresh
     * (acceptable freshness for this product).
     *
     * <p>Exposed in {@code UserPublicResponse} and
     * {@code UserProfileResponse} so the frontend can render role-driven
     * UI (currently the "Staff" badge on any admin's profile, regardless
     * of the viewer's own role).
     *
     * <p>Initialised to an empty list (matches Hibernate's hydration
     * behaviour for {@code @ElementCollection} fields with no entry in
     * the join table) so consumer DTOs and the role-sync helper can
     * treat it as always-non-null and skip null guards. The Panache
     * field-access rewrite makes those null guards unreachable at
     * runtime anyway, which JaCoCo would otherwise report as a missed
     * branch on every read site.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    public List<String> roles = new java.util.ArrayList<>();

    public String avatarUrl;
    public String bannerUrl;

    @Column(nullable = false)
    public boolean profilePublic = false;

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt = LocalDateTime.now(ZoneId.systemDefault());

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
     * Batched case-insensitive lookup (SCRUM-145). Used by
     * {@code GET /users/_internal-by-usernames} to resolve {@code N}
     * handles to their UUIDs in a single round-trip — avoids the N+1
     * pattern that would otherwise materialise from notification-service
     * iterating {@code GET /users/by-username/{u}} once per mention.
     *
     * <p>Inputs are lowercased and deduplicated before the {@code IN}
     * clause ; nulls and blanks are skipped. Missing handles are simply
     * absent from the result list — the consumer drops them silently
     * (a comment mentioning {@code @ghost.user} just doesn't produce
     * a notification, no error). Returns an empty list when no usable
     * handles remain.
     */
    public static List<User> findByUsernames(Collection<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return Collections.emptyList();
        }
        // Locale.ROOT — usernames are ASCII [a-z0-9._-], the server locale
        // must not influence the lowercasing (Turkish-i bug class).
        List<String> normalised = usernames.stream()
                .filter(u -> u != null && !u.isBlank())
                .map(u -> u.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        if (normalised.isEmpty()) {
            return Collections.emptyList();
        }
        return list("username in ?1", normalised);
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
