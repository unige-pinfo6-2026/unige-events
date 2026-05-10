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
}
