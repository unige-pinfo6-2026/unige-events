package ch.unige.events.event.view.resource;

import ch.unige.events.event.view.dto.RecordViewRequest;
import ch.unige.events.event.view.service.EventViewService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

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
    @PermitAll
    public Response recordView(@PathParam("id") Long id, RecordViewRequest body) {
        String auth0Id = identity.isAnonymous() ? null : identity.getPrincipal().getName();
        UUID sessionId = body != null ? body.sessionId() : null;
        eventViewService.recordView(auth0Id, id, sessionId);
        return Response.noContent().build();
    }
}
