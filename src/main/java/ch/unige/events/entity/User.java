package ch.unige.events.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    public String faculty;
    public String studyLevel;

    @Column(columnDefinition = "TEXT")
    public String bio;

    @ElementCollection(fetch = FetchType.EAGER)
    public List<String> interests;

    public String avatarUrl;

    @Column(nullable = false)
    public boolean profilePublic = false;

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    @Version
    @Column(nullable = false)
    public long version = 0L;

    public static Optional<User> findByAuth0Id(String auth0Id) {
        return find("auth0Id", auth0Id).firstResultOptional();
    }

    public static Optional<User> findByEmail(String email) {
        return find("email", email).firstResultOptional();
    }
}