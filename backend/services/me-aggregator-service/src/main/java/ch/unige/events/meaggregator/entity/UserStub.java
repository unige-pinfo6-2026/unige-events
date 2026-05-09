package ch.unige.events.meaggregator.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Optional;
import java.util.UUID;

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
