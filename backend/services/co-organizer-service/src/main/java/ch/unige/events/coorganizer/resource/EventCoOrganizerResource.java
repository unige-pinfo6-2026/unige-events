package ch.unige.events.coorganizer.resource;

import ch.unige.events.coorganizer.dto.CoOrganizerDTO;
import ch.unige.events.coorganizer.dto.InviteCoOrganizerRequest;
import ch.unige.events.coorganizer.service.EventCoOrganizerService;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

@Path("/events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EventCoOrganizerResource {

    private final EventCoOrganizerService service;
    private final SecurityIdentity identity;

    @Inject
    public EventCoOrganizerResource(EventCoOrganizerService service, SecurityIdentity identity) {
        this.service = service;
        this.identity = identity;
    }

    @POST
    @Path("/{id}/co-organizers")
    @Authenticated
    public Response invite(@PathParam("id") Long eventId, @Valid @NotNull InviteCoOrganizerRequest request) {
        String auth0Id = identity.getPrincipal().getName();
        boolean isAdmin = identity.hasRole("ADMIN");
        CoOrganizerDTO created = service.invite(eventId, auth0Id, request.userId(), isAdmin);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @GET
    @Path("/{id}/co-organizers")
    @Authenticated
    public List<CoOrganizerDTO> list(@PathParam("id") Long eventId) {
        return service.getCoOrganizers(eventId);
    }

    @DELETE
    @Path("/{id}/co-organizers/{userId}")
    @Authenticated
    public Response remove(@PathParam("id") Long eventId, @PathParam("userId") UUID userId) {
        String auth0Id = identity.getPrincipal().getName();
        boolean isAdmin = identity.hasRole("ADMIN");
        service.remove(eventId, auth0Id, userId, isAdmin);
        return Response.noContent().build();
    }

    @PATCH
    @Path("/{id}/co-organizers/me/accept")
    @Authenticated
    @Consumes(MediaType.WILDCARD)
    public Response accept(@PathParam("id") Long eventId) {
        String auth0Id = identity.getPrincipal().getName();
        CoOrganizerDTO accepted = service.accept(eventId, auth0Id);
        return Response.ok(accepted).build();
    }

    @PATCH
    @Path("/{id}/co-organizers/me/decline")
    @Authenticated
    @Consumes(MediaType.WILDCARD)
    public Response decline(@PathParam("id") Long eventId) {
        String auth0Id = identity.getPrincipal().getName();
        service.decline(eventId, auth0Id);
        return Response.noContent().build();
    }
}
