package ch.unige.events.event.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only stub of the User entity. event-service needs:
 *
 * <ul>
 *   <li>{@code auth0_id} — to detect the creator-cascade self-case in
 *       {@code isCreator(event, auth0Id)}.
 *   <li>The {@code @ManyToOne} target type for {@link Event#creator},
 *       so the JPA mapping resolves at startup.
 * </ul>
 *
 * <p>Replaced by REST client to user-service at PR 12 (already extracted)
 * in a follow-up cleanup ; for S8 we still read directly from the shared
 * `users` table to keep startup self-contained.
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
