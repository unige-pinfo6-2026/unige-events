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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class SchemaFixupTest {

    @Inject
    SchemaFixup schemaFixup;

    @Inject
    DataSource dataSource;

    @Inject
    EntityManager entityManager;

    @Test
    void reconcile_isIdempotent_whenInvokedTwice() {
        // Already invoked once at startup; calling reconcile() twice more must not throw.
        assertDoesNotThrow(schemaFixup::reconcile);
        assertDoesNotThrow(schemaFixup::reconcile);
    }

    @Test
    void attendancesStatusCheck_existsAndAllowsBothEnumValues() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT pg_get_constraintdef(c.oid) "
                             + "FROM pg_constraint c "
                             + "JOIN pg_class t ON c.conrelid = t.oid "
                             + "WHERE t.relname = 'attendances' "
                             + "AND c.conname = 'attendances_status_check'")) {
            assertTrue(rs.next(), "attendances_status_check constraint must exist after startup");
            String def = rs.getString(1);
            assertNotNull(def);
            assertTrue(def.contains("ATTENDING"),
                    "Constraint must allow ATTENDING — got: " + def);
            assertTrue(def.contains("WAITLISTED"),
                    "Constraint must allow WAITLISTED — got: " + def);
        }
    }

    @Test
    @TestTransaction
    void persistingWaitlistedAttendance_doesNotViolateConstraint() {
        // Sanity check at the persistence layer: a flush of an Attendance
        // with status = WAITLISTED must not raise a CHECK violation.
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
