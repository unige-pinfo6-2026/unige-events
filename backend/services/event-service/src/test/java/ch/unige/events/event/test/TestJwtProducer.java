package ch.unige.events.event.test;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Test-only CDI bean that produces the {@link JsonWebToken} looked up by
 * the event-service services via {@code Instance<JsonWebToken>}.
 *
 * <p>Since {@code %test.quarkus.oidc.enabled=false} the OIDC JWT producer
 * is not wired in tests. This producer plugs the gap by delegating to
 * {@link JwtTestContext}.
 */
@ApplicationScoped
@Unremovable
public class TestJwtProducer {

    @Produces
    @RequestScoped
    public JsonWebToken produceJwt() {
        JsonWebToken staged = JwtTestContext.get();
        return staged != null ? staged : JwtTestHelper.anonymous();
    }
}
