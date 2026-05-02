package ch.unige.events.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User extends PanacheEntityBase {

    public static final String USERNAME_PATTERN = "^[a-z0-9._-]{3,30}$";

    @Id
    @GeneratedValue
    public UUID id;

    @Column(nullable = false, unique = true, updatable = false)
    public String auth0Id;

    @Column(nullable = false, unique = true, updatable = false)
    public String email;

    @NotBlank
    @Pattern(regexp = USERNAME_PATTERN, message = "must match " + USERNAME_PATTERN)
    @Column(nullable = false, unique = true)
    public String username;

    public String displayName;
    public String firstName;
    public String lastName;
    public String faculty;
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

    public static Optional<User> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return find("LOWER(username)", username.toLowerCase()).firstResultOptional();
    }

    public static boolean existsByUsername(String username) {
        if (username == null) {
            return false;
        }
        return count("LOWER(username)", username.toLowerCase()) > 0;
    }
}
