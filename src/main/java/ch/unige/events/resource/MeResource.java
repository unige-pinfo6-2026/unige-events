package ch.unige.events.resource;

import ch.unige.events.dto.MeResponse;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/me")
@Authenticated
public class MeResource {

    @Inject
    SecurityIdentity identity;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public MeResponse me() {
        var principal = identity.getPrincipal();
        String sub = principal.getName();
        String email = null;
        String name = null;
        if (principal instanceof JsonWebToken jwt) {
            email = jwt.getClaim("email");
            name = jwt.getClaim("name");
        }
        return new MeResponse(sub, email, name);
    }
}
