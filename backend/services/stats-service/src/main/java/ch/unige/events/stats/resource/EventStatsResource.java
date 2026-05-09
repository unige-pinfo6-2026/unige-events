package ch.unige.events.stats.resource;

import ch.unige.events.stats.dto.EventStatsDTO;
import ch.unige.events.stats.service.EventStatsService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EventStatsResource {

    private final EventStatsService eventStatsService;
    private final SecurityIdentity identity;

    @Inject
    public EventStatsResource(EventStatsService eventStatsService, SecurityIdentity identity) {
        this.eventStatsService = eventStatsService;
        this.identity = identity;
    }

    @GET
    @Path("/{id}/stats")
    @Authenticated
    public Response getStats(@PathParam("id") Long id) {
        String auth0Id = identity.getPrincipal().getName();
        EventStatsDTO dto = eventStatsService.getStats(auth0Id, id);
        return Response.ok(dto).build();
    }
}
