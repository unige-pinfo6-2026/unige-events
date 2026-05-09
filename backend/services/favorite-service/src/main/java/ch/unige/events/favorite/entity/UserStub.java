package ch.unige.events.favorite.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only stub of the User entity (id + auth0_id only). favorite-service
 * needs to resolve the JWT's auth0 sub claim → users.id (UUID) before it
 * can write a Favorite or look up the caller's favorites. Full User entity
 * lives in user-service (PR 12) — at that point this stub is replaced by
 * a REST client to user-service.
 */
@Entity
@Table(name = "users")
public class UserStub extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "auth0_id", nullable = false, unique = true, updatable = false)
    public String auth0Id;

    public static Optional<UserStub> findByAuth0Id(String auth0Id) {
        return find("auth0Id", auth0Id).firstResultOptional();
    }
}
