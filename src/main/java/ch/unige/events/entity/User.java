package ch.unige.events.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(nullable = false, unique = true)
    public String email;

    public String displayName;
    public String faculty;
    public String studyLevel;

    @Column(columnDefinition = "TEXT")
    public String bio;

    @Column(columnDefinition = "TEXT")
    public String interests;

    public String avatarUrl;

    @Column(nullable = false)
    public boolean isAdmin = false;

    @Column(nullable = false)
    public boolean isProfilePublic = true;

    @Column(nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    // ── Helpers ──────────────────────────────────────────

    public static Optional<User> findByEmail(String email) {
        return find("email", email).firstResultOptional();
    }
}