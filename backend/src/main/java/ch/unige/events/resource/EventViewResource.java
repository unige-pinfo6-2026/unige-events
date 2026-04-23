package ch.unige.events.resource;

import ch.unige.events.service.EventViewService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EventViewResource {

    private final EventViewService eventViewService;
    private final SecurityIdentity identity;

    @Inject
    public EventViewResource(EventViewService eventViewService, SecurityIdentity identity) {
        this.eventViewService = eventViewService;
        this.identity = identity;
    }

    @POST
    @Path("/{id}/view")
    @Authenticated
    @Consumes(MediaType.WILDCARD)   // pas de body — surcharge le @Consumes(JSON) de la classe
    public Response recordView(@PathParam("id") Long id) {
        String auth0Id = identity.getPrincipal().getName();
        eventViewService.recordView(auth0Id, id);
        return Response.noContent().build();
    }
}
