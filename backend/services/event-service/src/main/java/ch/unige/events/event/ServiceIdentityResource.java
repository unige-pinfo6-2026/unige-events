package ch.unige.events.event;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

/**
 * Identity probe for event-service. Returned by {@code GET /api/__service}.
 * Lets Kong / curl confirm which microservice answers a given path
 * during the strangler-fig cutover (cf.
 * specs_archives/specs_claude/specs_microservices_migration.md decision 20).
 */
@Path("/__service")
@Produces(MediaType.APPLICATION_JSON)
public class ServiceIdentityResource {

    @GET
    @PermitAll
    public Map<String, String> identity() {
        return Map.of(
            "service", "event-service",
            "module", "ch.unige.events:event-service",
            "status", "scaffold");
    }
}
