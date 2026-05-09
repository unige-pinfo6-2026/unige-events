package ch.unige.events.coorganizer.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only stub of the User entity. co-organizer-service needs auth0_id
 * to detect the inviter (creator-of-event) self-case, plus displayName +
 * avatarUrl to project CoOrganizerDTO. Replaced by REST client to
 * user-service at PR 12.
 */
@Entity
@Table(name = "users")
public class UserStub extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "auth0_id", nullable = false, unique = true, updatable = false)
    public String auth0Id;

    @Column(name = "display_name")
    public String displayName;

    @Column(name = "avatar_url")
    public String avatarUrl;

    public static Optional<UserStub> findByAuth0Id(String auth0Id) {
        return find("auth0Id", auth0Id).firstResultOptional();
    }
}
