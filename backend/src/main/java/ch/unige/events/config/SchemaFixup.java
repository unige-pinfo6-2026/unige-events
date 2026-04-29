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

    // Note: events.category and events.status are not annotated @Column(nullable=false)
    // on the entity. The CHECKs below validate VALUE membership only — PostgreSQL's
    // NULL IN (...) evaluates to UNKNOWN and silently passes a CHECK, so NULL slips
    // through these constraints. Non-null is enforced at the application boundary via
    // @NotNull on the DTOs + EventService.collectPublishValidationErrors. Lifting it
    // to DB level would require @Column(nullable=false) on the entity (out of scope:
    // SCRUM-164 touches no entity), and would conflict with existing tests that
    // exercise the application's validation path on transiently invalid states.
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
     *
     * <p>Each (drop, add) pair is executed inside its own try/catch so that a
     * single ADD failure (e.g. a pre-existing row that violates the new check)
     * does not leave the database without checks on the other columns. The
     * surrounding try-with-resources only handles connection-level failures.
     */
    public void reconcile() {
        int recreated = 0;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            for (int i = 0; i < DROP_OBSOLETE_CONSTRAINTS.length; i++) {
                try {
                    stmt.execute(DROP_OBSOLETE_CONSTRAINTS[i]);
                    stmt.execute(RECREATE_CONSTRAINTS[i]);
                    recreated++;
                } catch (SQLException e) {
                    // Non-fatal: typically caused by pre-existing rows that violate
                    // the new check. The dropped constraint is not re-added on this
                    // boot — surfaced via WARN so the operator can inspect/fix data.
                    LOG.warnf(e, "Skipped CHECK constraint reconciliation #%d: %s",
                            i, e.getMessage());
                }
            }
            LOG.infof("Schema check constraints reconciled (%d/%d): "
                    + "events.faculty/category/status, attendances.status.",
                    recreated, RECREATE_CONSTRAINTS.length);
        } catch (SQLException e) {
            // Connection-level failure: tables may not yet exist on a brand-new DB
            // when this hook runs in some Quarkus startup orderings.
            LOG.warnf(e, "Schema reconciliation skipped: %s", e.getMessage());
        }
    }
}
