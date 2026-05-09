package ch.unige.events.follow.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only stub of the User entity. follow-service needs:
 *
 * <ul>
 *   <li>{@code profilePublic} — to decide whether a new follow row enters
 *       PENDING (private target) or ACCEPTED (public target), and to apply
 *       the ISSUE-93 anti-oracle 404 on {@code GET /users/{id}/followers}
 *       / {@code following}.
 *   <li>{@code displayName, faculty, studyLevel, bio, interests, avatarUrl,
 *       bannerUrl} — to project rows in the followers / following lists
 *       (UserPublicResponse).
 *   <li>{@code auth0_id} — to detect the self-case in the visibility check.
 * </ul>
 *
 * <p>Replaced by REST clients to user-service at PR 12 :
 * {@code GET /users/{id}} for the visibility / projection lookup, and
 * {@code GET /internal/users/by-id?ids=...} for the bulk resolveUsers
 * fan-out.
 */
@Entity
@Table(name = "users")
public class UserStub extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "auth0_id", nullable = false, unique = true, updatable = false)
    public String auth0Id;

    @Column(name = "profile_public", nullable = false)
    public boolean profilePublic;

    @Column(name = "display_name")
    public String displayName;

    public String faculty;

    @Column(name = "study_level")
    public String studyLevel;

    @Column(columnDefinition = "TEXT")
    public String bio;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_interests", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "interests")
    public List<String> interests;

    @Column(name = "avatar_url")
    public String avatarUrl;

    @Column(name = "banner_url")
    public String bannerUrl;

    public static Optional<UserStub> findByAuth0Id(String auth0Id) {
        return find("auth0Id", auth0Id).firstResultOptional();
    }
}
