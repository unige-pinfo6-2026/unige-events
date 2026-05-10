package ch.unige.events.report.test;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Test-only CDI bean that produces the {@link JsonWebToken} looked up by
 * {@link ch.unige.events.report.service.ReportService} via
 * {@code Instance<JsonWebToken>}.
 *
 * <p>{@code %test.quarkus.oidc.enabled=false} so the OIDC producer is
 * absent; this stand-in delegates to {@link JwtTestContext}.
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
