package ch.unige.events.share.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Optional;

/**
 * Minimal stub of the Event entity for share-service. Owns only the two
 * columns share-service needs to read/write : id (PK) and shareCode (the
 * unique 8-char token resolved by RedirectResource).
 *
 * <p>The full Event entity lives in services/legacy-monolith/ today and
 * will move to services/event-service/ at step 14 of the migration. Until
 * then, share-service shares the `events` table in the public schema with
 * legacy-monolith — Hibernate validate mode only checks the columns this
 * stub declares (id + share_code), so the legacy table's other columns
 * are invisible from here.</p>
 */
@Entity
@Table(name = "events")
public class Event extends PanacheEntity {

    @Column(name = "share_code", unique = true)
    public String shareCode;

    public static Optional<Event> findByShareCode(String shareCode) {
        return find("shareCode", shareCode).firstResultOptional();
    }
}
