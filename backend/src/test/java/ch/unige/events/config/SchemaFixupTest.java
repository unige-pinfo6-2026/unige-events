package ch.unige.events.config;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link SchemaFixup}.
 *
 * <p>Verifies that the four CHECK constraints reconciled at startup
 * ({@code events_faculty_check}, {@code events_category_check},
 * {@code events_status_check}, {@code attendances_status_check}) are
 * present in PostgreSQL with the current enum values, accept all valid
 * values, and reject invalid ones — using native SQL INSERTs to bypass
 * JPA-level validation and exercise the DB-level guard.
 */
@QuarkusTest
class SchemaFixupTest {

    /**
     * High-range counter for native INSERTs. Avoids colliding with
     * sequence-generated IDs (which start at 1) and dodges any naming
     * uncertainty around the Hibernate-managed sequence.
     */
    private static final AtomicLong NATIVE_ID_SEQ = new AtomicLong(900_000_000L);

    @Inject
    SchemaFixup schemaFixup;

    @Inject
    DataSource dataSource;

    // --- Idempotence ---

    @Test
    void reconcile_isIdempotent_whenInvokedTwice() {
        // Already invoked once at startup; calling reconcile() twice more must not throw.
        assertDoesNotThrow(schemaFixup::reconcile);
        assertDoesNotThrow(schemaFixup::reconcile);
    }

    // --- events_faculty_check ---

    @Test
    void eventsFacultyCheck_existsAfterStartup() throws SQLException {
        assertConstraintDefContains("events", "events_faculty_check",
                "SCIENCES", "MEDICINE", "LETTERS", "SOCIAL_SCIENCES",
                "GSEM", "LAW", "THEOLOGY", "PSYCHOLOGY", "FTI");
    }

    @Test
    void eventsFacultyCheck_acceptsNull() {
        // faculty is nullable — the constraint must not block NULL.
        assertDoesNotThrow(() -> insertNativeEvent("ACADEMIC", "DRAFT", null));
    }

    @Test
    void eventsFacultyCheck_rejectsInvalidValue() {
        assertCheckViolation(() -> insertNativeEvent("ACADEMIC", "DRAFT", "NOT_A_FACULTY"));
    }

    // --- events_category_check ---

    @Test
    void eventsCategoryCheck_existsAfterStartup() throws SQLException {
        assertConstraintDefContains("events", "events_category_check",
                "ACADEMIC", "SPORTS", "CULTURAL", "SOCIAL", "CONFERENCE", "OTHER");
    }

    @Test
    void eventsCategoryCheck_rejectsInvalidValue() {
        assertCheckViolation(() -> insertNativeEvent("NOT_A_CATEGORY", "DRAFT", null));
    }

    // --- events_status_check ---

    @Test
    void eventsStatusCheck_existsAfterStartup() throws SQLException {
        assertConstraintDefContains("events", "events_status_check",
                "DRAFT", "PUBLISHED", "CANCELLED");
    }

    @Test
    void eventsStatusCheck_rejectsInvalidValue() {
        assertCheckViolation(() -> insertNativeEvent("ACADEMIC", "ARCHIVED", null));
    }

    // --- attendances_status_check (also covered by feature/s5-attendees-list cf83098 ;
    //     duplicated here while that PR is unmerged on main, to remove on rebase
    //     if the other PR merges first — cf. spec decision 13) ---

    @Test
    void attendancesStatusCheck_existsAfterStartup() throws SQLException {
        assertConstraintDefContains("attendances", "attendances_status_check",
                "ATTENDING", "WAITLISTED");
    }

    // --- Helpers ---

    /**
     * Asserts that the given CHECK constraint exists on the given table and
     * its definition contains every expected enum value as a literal.
     */
    private void assertConstraintDefContains(String table, String constraint, String... expectedValues)
            throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT pg_get_constraintdef(c.oid) "
                             + "FROM pg_constraint c "
                             + "JOIN pg_class t ON c.conrelid = t.oid "
                             + "WHERE t.relname = '" + table + "' "
                             + "AND c.conname = '" + constraint + "'")) {
            assertTrue(rs.next(),
                    constraint + " must exist on table " + table + " after startup");
            String def = rs.getString(1);
            assertNotNull(def);
            for (String value : expectedValues) {
                assertTrue(def.contains(value),
                        "Constraint " + constraint + " must allow " + value + " — got: " + def);
            }
        }
    }

    /**
     * Inserts an Event row via native SQL bypassing JPA validation.
     * The PK is drawn from {@link #NATIVE_ID_SEQ} to avoid any reliance on
     * the Hibernate-managed sequence name.
     *
     * @param category value to insert in {@code events.category}
     * @param status   value to insert in {@code events.status}
     * @param faculty  value to insert in {@code events.faculty} (may be {@code null})
     */
    private void insertNativeEvent(String category, String status, String faculty) throws SQLException {
        long id = NATIVE_ID_SEQ.incrementAndGet();
        String facultySql = (faculty == null) ? "NULL" : "'" + faculty + "'";
        String sql = "INSERT INTO events (id, title, location, start_date, end_date, "
                + "category, status, faculty, all_day, created_at, updated_at) "
                + "VALUES (" + id + ", 'native-test', 'Uni Mail', "
                + "now() + interval '1 day', now() + interval '2 day', "
                + "'" + category + "', '" + status + "', " + facultySql + ", false, now(), now())";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    /**
     * Asserts that the given runnable raises a SQLException whose chain mentions
     * a check constraint violation. PostgreSQL surfaces this as
     * {@code SQLState '23514'}.
     */
    private void assertCheckViolation(SqlRunnable runnable) {
        SQLException raised = assertThrows(SQLException.class, runnable::run);
        SQLException cause = raised;
        while (cause != null) {
            String state = cause.getSQLState();
            String message = cause.getMessage();
            if ("23514".equals(state)
                    || (message != null && message.contains("check constraint"))) {
                return;
            }
            cause = cause.getNextException();
        }
        throw new AssertionError("Expected check constraint violation, got: " + raised, raised);
    }

    @FunctionalInterface
    private interface SqlRunnable {
        void run() throws SQLException;
    }
}
