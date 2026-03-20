package ch.unige.events.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends PanacheEntityBase {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true, updatable = false)
    private String auth0Id;

    @Column(nullable = false, unique = true, updatable = false)
    private String email;

    private String displayName;
    private String firstName;
    private String lastName;
    private String faculty;
    private String studyLevel;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(columnDefinition = "TEXT")
    private String interests;

    private String avatarUrl;

    @Column(nullable = false)
    @Builder.Default
    private boolean admin = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean profilePublic = false;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Version
    @Column(nullable = false)
    @Builder.Default
    private long version = 0L;

    // ── Helpers ──────────────────────────────────────────

    public static Optional<User> findByAuth0Id(String auth0Id) {
        return find("auth0Id", auth0Id).firstResultOptional();
    }

    public static Optional<User> findByEmail(String email) {
        return find("email", email).firstResultOptional();
    }
}