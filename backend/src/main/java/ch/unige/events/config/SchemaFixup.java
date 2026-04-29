package ch.unige.events.config;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Reconciles obsolete CHECK constraints left behind by Hibernate's
 * {@code update} schema-management strategy after enum values change.
 *
 * <p>Hibernate adds CHECK constraints on the initial table creation for
 * columns mapped with {@code @Enumerated(STRING)}, but never updates them when
 * enum values are added or renamed afterwards. As a result, an INSERT with a
 * new enum value can fail at flush time with a {@code *_check} violation on
 * databases provisioned before the value was introduced — and conversely, an
 * INSERT with a removed enum value can pass undetected when the obsolete
 * constraint has been dropped without replacement.
 *
 * <p>This class drops obsolete constraints (idempotent) and recreates the
 * canonical {@code events_*_check} and {@code attendances_status_check}
 * constraints with the current enum values to keep DB-level validation in
 * place. Can be removed once the project adopts a real migration tool
 * (Flyway / Liquibase).
 *
 * <p>Adding a new value to {@link ch.unige.events.entity.Faculty},
 * {@link ch.unige.events.entity.EventCategory},
 * {@link ch.unige.events.entity.EventStatus} or
 * {@link ch.unige.events.entity.AttendanceStatus} <strong>requires</strong> a
 * matching update of the constants below — the test suite will fail otherwise.
 */
@ApplicationScoped
public class SchemaFixup {

    private static final Logger LOG = Logger.getLogger(SchemaFixup.class);

    /**
     * Static DDL statements only — never concatenate user input here.
     * Naming follows PostgreSQL's auto-generated convention
     * ({@code <table>_<column>_check}).
     */
    static final String[] DROP_OBSOLETE_CONSTRAINTS = {
            "ALTER TABLE events DROP CONSTRAINT IF EXISTS events_faculty_check",
            "ALTER TABLE events DROP CONSTRAINT IF EXISTS events_category_check",
            "ALTER TABLE events DROP CONSTRAINT IF EXISTS events_status_check",
            "ALTER TABLE attendances DROP CONSTRAINT IF EXISTS attendances_status_check",
    };

    static final String RECREATE_EVENTS_FACULTY_CHECK =
            "ALTER TABLE events ADD CONSTRAINT events_faculty_check "
                    + "CHECK (faculty IS NULL OR faculty IN ("
                    + "'SCIENCES','MEDICINE','LETTERS','SOCIAL_SCIENCES',"
                    + "'GSEM','LAW','THEOLOGY','PSYCHOLOGY','FTI'))";

    static final String RECREATE_EVENTS_CATEGORY_CHECK =
            "ALTER TABLE events ADD CONSTRAINT events_category_check "
                    + "CHECK (category IN ("
                    + "'ACADEMIC','SPORTS','CULTURAL','SOCIAL','CONFERENCE','OTHER'))";

    static final String RECREATE_EVENTS_STATUS_CHECK =
            "ALTER TABLE events ADD CONSTRAINT events_status_check "
                    + "CHECK (status IN ('DRAFT','PUBLISHED','CANCELLED'))";

    static final String RECREATE_ATTENDANCE_STATUS_CHECK =
            "ALTER TABLE attendances ADD CONSTRAINT attendances_status_check "
                    + "CHECK (status IN ('ATTENDING','WAITLISTED'))";

    static final String[] RECREATE_CONSTRAINTS = {
            RECREATE_EVENTS_FACULTY_CHECK,
            RECREATE_EVENTS_CATEGORY_CHECK,
            RECREATE_EVENTS_STATUS_CHECK,
            RECREATE_ATTENDANCE_STATUS_CHECK,
    };

    @Inject
    DataSource dataSource;

    void onStart(@Observes StartupEvent ev) {
        reconcile();
    }

    /**
     * Drops obsolete CHECK constraints and recreates the canonical
     * {@code events_*_check} and {@code attendances_status_check} constraints
     * with the current enum values. Idempotent: safe to call multiple times.
     */
    public void reconcile() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String ddl : DROP_OBSOLETE_CONSTRAINTS) {
                stmt.execute(ddl);
            }
            for (String ddl : RECREATE_CONSTRAINTS) {
                stmt.execute(ddl);
            }
            LOG.info("Schema check constraints reconciled "
                    + "(events.faculty/category/status, attendances.status).");
        } catch (SQLException e) {
            // Non-fatal: log and continue. Tables may not yet exist on a brand-new DB
            // when this hook runs in some Quarkus startup orderings, or pre-existing
            // invalid rows may block ADD CONSTRAINT — both cases are observable via WARN.
            LOG.warnf(e, "Schema reconciliation skipped: %s", e.getMessage());
        }
    }
}
