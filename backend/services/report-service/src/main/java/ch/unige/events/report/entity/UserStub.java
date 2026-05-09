package ch.unige.events.report.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only stub of the User entity. report-service needs:
 *
 * <ul>
 *   <li>{@code auth0_id} — resolve the reporter / admin caller.
 *   <li>{@code displayName, firstName, lastName, email} — ReportDTO
 *       projects {@code reporterDisplayName} via fallback chain
 *       displayName → "first last" → email.
 * </ul>
 *
 * <p>Replaced by REST client to user-service at PR 12.
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

    @Column(name = "first_name")
    public String firstName;

    @Column(name = "last_name")
    public String lastName;

    @Column(nullable = false, unique = true, updatable = false)
    public String email;

    public static Optional<UserStub> findByAuth0Id(String auth0Id) {
        return find("auth0Id", auth0Id).firstResultOptional();
    }
}
