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
public class EventViewResource {

    private final EventViewService eventViewService;
    private final SecurityIdentity identity;

    @Inject
    public EventViewResource(EventViewService eventViewService, SecurityIdentity identity) {
        this.eventViewService = eventViewService;
        this.identity = identity;
    }

    /**
     * Records a view of an event. Body is optional — both anonymous (no JWT,
     * no body, no session) and authenticated calls are accepted.
     *
     * <p>{@code @Consumes(WILDCARD)} on the method (not the class) so that
     * empty POSTs without {@code Content-Type: application/json} don't trip
     * a 415 before reaching the handler.
     */
    @POST
    @Path("/{id}/view")
    @PermitAll
    @Consumes(MediaType.WILDCARD)
    public Response recordView(@PathParam("id") Long id, RecordViewRequest body) {
        String auth0Id = identity.isAnonymous() ? null : identity.getPrincipal().getName();
        UUID sessionId = body != null ? body.sessionId() : null;
        eventViewService.recordView(auth0Id, id, sessionId);
        return Response.noContent().build();
    }
}
