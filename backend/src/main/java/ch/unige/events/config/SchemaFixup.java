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
 * new enum value can fail at flush time with a {@code *_status_check}
 * violation on databases provisioned before the value was introduced.
 *
 * <p>This class drops obsolete constraints (idempotent) and recreates the
 * {@code attendances_status_check} constraint with the current enum values to
 * keep DB-level validation in place. Remediation for the {@code WAITLISTED}
 * value rollout (parent branch {@code feature/s6-capacity-waitlist}). Can be
 * removed once the project adopts a real migration tool.
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

    static final String RECREATE_ATTENDANCE_STATUS_CHECK =
            "ALTER TABLE attendances ADD CONSTRAINT attendances_status_check "
                    + "CHECK (status IN ('ATTENDING','WAITLISTED'))";

    @Inject
    DataSource dataSource;

    void onStart(@Observes StartupEvent ev) {
        reconcile();
    }

    /**
     * Drops obsolete CHECK constraints and recreates
     * {@code attendances_status_check} with the current enum values.
     * Idempotent: safe to call multiple times.
     */
    public void reconcile() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String ddl : DROP_OBSOLETE_CONSTRAINTS) {
                stmt.execute(ddl);
            }
            stmt.execute(RECREATE_ATTENDANCE_STATUS_CHECK);
            LOG.info("Attendance status check constraint reconciled (ATTENDING, WAITLISTED).");
        } catch (SQLException e) {
            // Non-fatal: log and continue. Tables may not yet exist on a brand-new DB
            // when this hook runs in some Quarkus startup orderings.
            LOG.warnf(e, "Schema reconciliation skipped: %s", e.getMessage());
        }
    }
}
