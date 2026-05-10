package ch.unige.events.shared.platform;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;

/**
 * Identity probe at {@code GET /api/__service}. Lets Kong / curl
 * confirm which microservice answers a given path during the
 * strangler-fig cutover. The service name is read from
 * {@code quarkus.application.name} so this single bean serves all 5
 * active microservices (4 actifs + 1 placeholder notification) without
 * per-service copies (REFACTOR-012, post-consolidation 14→5 cf.
 * sprint-context.md § Étape 23).
 */
@Path("/__service")
@Produces(MediaType.APPLICATION_JSON)
public class ServiceIdentityResource {

    @Inject
    @ConfigProperty(name = "quarkus.application.name", defaultValue = "unknown-service")
    String serviceName;

    @GET
    @PermitAll
    public Map<String, String> identity() {
        return Map.of(
                "service", serviceName,
                "module", "ch.unige.events:" + serviceName);
    }
}
