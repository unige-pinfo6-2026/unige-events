package ch.unige.events.config;

import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.AttendanceStatus;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the schema state Quarkus boots with after Flyway migrations have run.
 * <p>
 * In tests the DB is bootstrapped by Hibernate (drop-and-create in %test) so the
 * CHECK constraints are recreated on every startup with the current enum values;
 * in dev/prod they are reconciled by V1__reconcile_check_constraints.sql. Either
 * way the constraints below must exist post-startup with the correct values.
 */
@QuarkusTest
class FlywayMigrationTest {

    @Inject
    DataSource dataSource;

    @Inject
    EntityManager entityManager;

    private String constraintDefinition(String tableName, String constraintName) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT pg_get_constraintdef(c.oid) "
                             + "FROM pg_constraint c "
                             + "JOIN pg_class t ON c.conrelid = t.oid "
                             + "WHERE t.relname = ? AND c.conname = ?")) {
            stmt.setString(1, tableName);
            stmt.setString(2, constraintName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getString(1);
            }
        }
    }

    @Test
    void attendancesStatusCheck_existsAndAllowsBothEnumValues() throws SQLException {
        String def = constraintDefinition("attendances", "attendances_status_check");
        assertNotNull(def, "attendances_status_check constraint must exist after startup");
        assertTrue(def.contains("ATTENDING"),
                "Constraint must allow ATTENDING — got: " + def);
        assertTrue(def.contains("WAITLISTED"),
                "Constraint must allow WAITLISTED — got: " + def);
    }

    @Test
    void eventsStatusCheck_existsAndAllowsAllEnumValues() throws SQLException {
        String def = constraintDefinition("events", "events_status_check");
        assertNotNull(def, "events_status_check constraint must exist after startup");
        assertTrue(def.contains("DRAFT"),
                "Constraint must allow DRAFT — got: " + def);
        assertTrue(def.contains("PUBLISHED"),
                "Constraint must allow PUBLISHED — got: " + def);
        assertTrue(def.contains("CANCELLED"),
                "Constraint must allow CANCELLED — got: " + def);
    }

    @Test
    @TestTransaction
    void persistingWaitlistedAttendance_doesNotViolateConstraint() {
        Attendance attendance = new Attendance();
        attendance.userId = UUID.randomUUID();
        attendance.eventId = 9_999_999L;
        attendance.status = AttendanceStatus.WAITLISTED;

        assertDoesNotThrow(() -> {
            entityManager.persist(attendance);
            entityManager.flush();
        });
        assertNotNull(attendance.id);
    }
}
