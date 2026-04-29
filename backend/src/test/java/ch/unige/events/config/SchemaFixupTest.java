package ch.unige.events.config;

import ch.unige.events.entity.AttendanceStatus;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.Faculty;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
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
 *
 * <p>The {@code _coversAllEnumValues} tests derive their expected literals
 * directly from the corresponding {@code Enum.values()} so any drift
 * between an enum and the matching {@code RECREATE_*} constant in
 * {@link SchemaFixup} is detected automatically.
 */
@QuarkusTest
class SchemaFixupTest {

    /**
     * High-range counter for native INSERTs. Avoids colliding with
     * sequence-generated IDs (which start at 1) and dodges any naming
     * uncertainty around the Hibernate-managed sequence.
     */
    private static final AtomicLong NATIVE_ID_SEQ = new AtomicLong(900_000_000L);

    /** Lower bound used by {@link #cleanupNativeRows()} to delete only test rows. */
    private static final long NATIVE_ID_FLOOR = 900_000_000L;

    @Inject
    SchemaFixup schemaFixup;

    @Inject
    DataSource dataSource;

    @AfterEach
    void cleanupNativeRows() throws SQLException {
        // Native INSERTs that succeed (e.g. faculty-NULL acceptance) persist outside
        // any JPA transaction; clean them up to keep the shared DevServices DB
        // independent across @QuarkusTest classes.
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM events WHERE id >= " + NATIVE_ID_FLOOR);
        }
    }

    // --- Idempotence ---

    @Test
    void reconcile_isIdempotent_whenInvokedTwice() {
        // Already invoked once at startup; calling reconcile() twice more must not throw.
        assertDoesNotThrow(schemaFixup::reconcile);
        assertDoesNotThrow(schemaFixup::reconcile);
    }

    // --- events_faculty_check ---

    @Test
    void eventsFacultyCheck_coversAllEnumValues() throws SQLException {
        assertConstraintAllowsAllEnumValues("events", "events_faculty_check", Faculty.values());
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
    void eventsCategoryCheck_coversAllEnumValues() throws SQLException {
        assertConstraintAllowsAllEnumValues("events", "events_category_check", EventCategory.values());
    }

    @Test
    void eventsCategoryCheck_rejectsInvalidValue() {
        assertCheckViolation(() -> insertNativeEvent("NOT_A_CATEGORY", "DRAFT", null));
    }

    // --- events_status_check ---

    @Test
    void eventsStatusCheck_coversAllEnumValues() throws SQLException {
        assertConstraintAllowsAllEnumValues("events", "events_status_check", EventStatus.values());
    }

    @Test
    void eventsStatusCheck_rejectsInvalidValue() {
        assertCheckViolation(() -> insertNativeEvent("ACADEMIC", "ARCHIVED", null));
    }

    // --- attendances_status_check (also covered by feature/s5-attendees-list cf83098 ;
    //     duplicated here while that PR is unmerged on main, to remove on rebase
    //     if the other PR merges first — cf. spec decision 13) ---

    @Test
    void attendancesStatusCheck_coversAllEnumValues() throws SQLException {
        assertConstraintAllowsAllEnumValues("attendances", "attendances_status_check",
                AttendanceStatus.values());
    }

    // --- Helpers ---

    /**
     * Asserts that the given CHECK constraint exists on the given table and
     * its definition contains every value of the supplied enum as a literal.
     * Drift-detects new enum values that were not mirrored into the
     * corresponding {@code RECREATE_*} constant in {@link SchemaFixup}.
     */
    private void assertConstraintAllowsAllEnumValues(String table, String constraint, Enum<?>[] values)
            throws SQLException {
        String def = readConstraintDef(table, constraint);
        assertNotNull(def, constraint + " must exist on table " + table + " after startup");
        for (Enum<?> value : values) {
            assertTrue(def.contains(value.name()),
                    "Constraint " + constraint + " must allow " + value.name() + " — got: " + def);
        }
    }

    /**
     * Returns the {@code pg_get_constraintdef} string for the given table-named
     * CHECK constraint, or {@code null} if it does not exist.
     */
    private String readConstraintDef(String table, String constraint) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT pg_get_constraintdef(c.oid) "
                             + "FROM pg_constraint c "
                             + "JOIN pg_class t ON c.conrelid = t.oid "
                             + "WHERE t.relname = '" + table + "' "
                             + "AND c.conname = '" + constraint + "'")) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    /**
     * Inserts an Event row via native SQL bypassing JPA validation.
     * The PK is drawn from {@link #NATIVE_ID_SEQ} to avoid any reliance on
     * the Hibernate-managed sequence name; rows are removed after each test
     * by {@link #cleanupNativeRows()}.
     *
     * @param category value to insert in {@code events.category} (or {@code null})
     * @param status   value to insert in {@code events.status}   (or {@code null})
     * @param faculty  value to insert in {@code events.faculty}  (or {@code null})
     */
    private void insertNativeEvent(String category, String status, String faculty) throws SQLException {
        long id = NATIVE_ID_SEQ.incrementAndGet();
        String sql = "INSERT INTO events (id, title, location, start_date, end_date, "
                + "category, status, faculty, all_day, created_at, updated_at) "
                + "VALUES (" + id + ", 'native-test', 'Uni Mail', "
                + "now() + interval '1 day', now() + interval '2 day', "
                + sqlLiteral(category) + ", " + sqlLiteral(status) + ", " + sqlLiteral(faculty)
                + ", false, now(), now())";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    /** Renders a value for a SQL VALUES clause: {@code NULL} or a single-quoted literal. */
    private String sqlLiteral(String value) {
        return value == null ? "NULL" : "'" + value + "'";
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
