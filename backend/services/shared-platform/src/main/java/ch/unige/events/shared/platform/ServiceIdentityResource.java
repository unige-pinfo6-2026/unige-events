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
 * {@code quarkus.application.name} so this single bean serves all 14
 * microservices without per-service copies (REFACTOR-012).
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
