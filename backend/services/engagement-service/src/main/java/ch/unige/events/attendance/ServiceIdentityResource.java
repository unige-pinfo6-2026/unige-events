package ch.unige.events.attendance;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

/**
 * Identity probe for engagement-service (renamed from attendance-service in
 * Étape 2.1.1 of the finalization spec). Returned by {@code GET /api/__service}.
 * Lets Kong / curl confirm which microservice answers a given path
 * during the strangler-fig cutover.
 * NB: package stays {@code ch.unige.events.attendance} until step 2.4.1
 * which renames the whole tree to {@code ch.unige.events.engagement.*}.
 */
@Path("/__service")
@Produces(MediaType.APPLICATION_JSON)
public class ServiceIdentityResource {

    @GET
    @PermitAll
    public Map<String, String> identity() {
        return Map.of(
            "service", "engagement-service",
            "module", "ch.unige.events:engagement-service",
            "status", "scaffold");
    }
}
