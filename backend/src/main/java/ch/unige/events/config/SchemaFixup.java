package ch.unige.events.config;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * Drops stale CHECK constraints left behind by Hibernate's "update" strategy
 * after enum values were renamed. Hibernate adds constraints for
 * {@code @Enumerated(STRING)} columns but never drops them when enum values
 * change, causing inserts to fail against the old value list.
 */
@ApplicationScoped
public class SchemaFixup {

    @Inject
    DataSource dataSource;

    void onStart(@Observes StartupEvent ev) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE events DROP CONSTRAINT IF EXISTS events_faculty_check");
            stmt.execute("ALTER TABLE events DROP CONSTRAINT IF EXISTS events_category_check");
            stmt.execute("ALTER TABLE events DROP CONSTRAINT IF EXISTS events_status_check");
        } catch (SQLException e) {
            // Non-fatal: constraint may not exist on fresh databases
        }
    }
}
