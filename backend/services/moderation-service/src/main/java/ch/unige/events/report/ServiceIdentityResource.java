package ch.unige.events.report;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

/**
 * Identity probe for moderation-service (renamed from report-service in
 * Étape 2.1.2 of the finalization spec). Returned by {@code GET /api/__service}.
 * Lets Kong / curl confirm which microservice answers a given path
 * during the strangler-fig cutover.
 * NB: package stays {@code ch.unige.events.report} — moderation-service
 * keeps the report-domain sources unchanged since it is not absorbed
 * by another service.
 */
@Path("/__service")
@Produces(MediaType.APPLICATION_JSON)
public class ServiceIdentityResource {

    @GET
    @PermitAll
    public Map<String, String> identity() {
        return Map.of(
            "service", "moderation-service",
            "module", "ch.unige.events:moderation-service",
            "status", "scaffold");
    }
}
