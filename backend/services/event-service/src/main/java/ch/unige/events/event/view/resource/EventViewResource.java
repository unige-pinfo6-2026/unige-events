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

/**
 * View counter endpoint. Two method variants are required by JAX-RS
 * dispatch — see comments below.
 */
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
     * Matches requests with {@code Content-Type: application/json} and a
     * {@code { "sessionId": "uuid" }} body. RestEasy invokes the JSON
     * MessageBodyReader for this method.
     */
    @POST
    @Path("/{id}/view")
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    public Response recordViewWithBody(@PathParam("id") Long id, RecordViewRequest body) {
        UUID sessionId = body != null ? body.sessionId() : null;
        return doRecord(id, sessionId);
    }

    /**
     * Fallback for requests without a JSON body — REST-Assured with no
     * {@code .body(...)} defaults to {@code application/x-www-form-urlencoded},
     * and a JSON-only @Consumes would reject that with 415. Declaring a
     * second method with no @Consumes accepts any content-type that's not
     * already routed to the JSON variant (typical: form-urlencoded, no
     * Content-Type header at all).
     */
    @POST
    @Path("/{id}/view")
    @PermitAll
    public Response recordViewWithoutBody(@PathParam("id") Long id) {
        return doRecord(id, null);
    }

    private Response doRecord(Long id, UUID sessionId) {
        String auth0Id = identity.isAnonymous() ? null : identity.getPrincipal().getName();
        eventViewService.recordView(auth0Id, id, sessionId);
        return Response.noContent().build();
    }
}
