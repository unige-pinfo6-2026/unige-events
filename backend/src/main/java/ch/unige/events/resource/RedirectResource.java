package ch.unige.events.resource;

import ch.unige.events.entity.Event;
import ch.unige.events.service.ShareService;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;

@Path("/s")
@Produces(MediaType.APPLICATION_JSON)
public class RedirectResource {

    private final ShareService shareService;

    @ConfigProperty(name = "app.frontend.url", defaultValue = "https://10.25.10.136.nip.io")
    String frontendUrl;

    @Inject
    public RedirectResource(ShareService shareService) {
        this.shareService = shareService;
    }

    @GET
    @Path("/{shortCode}")
    @PermitAll
    public Response redirect(@PathParam("shortCode") String shortCode) {
        Event event = shareService.resolveByShortCode(shortCode);
        URI location = URI.create(frontendUrl + "/events/" + event.id);
        return Response.status(Response.Status.FOUND).location(location).build();
    }
}
