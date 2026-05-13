package ch.unige.events.report.test;

import ch.unige.events.shared.domain.projections.CallerIdentity;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;

@Mock
@ApplicationScoped
public class TestCallerIdentity extends CallerIdentity {

    @Override
    public UUID requireUuid() {
        UUID id = currentUuid();
        if (id == null) {
            throw new NotFoundException("User profile not found");
        }
        return id;
    }

    @Override
    public UUID getUuid() {
        return currentUuid();
    }

    private UUID currentUuid() {
        JsonWebToken jwt = JwtTestContext.get();
        Object raw = jwt != null ? jwt.getClaim("uuid") : null;
        return raw != null ? UUID.fromString(raw.toString()) : null;
    }
}
