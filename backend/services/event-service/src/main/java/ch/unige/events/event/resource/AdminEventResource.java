package ch.unige.events.event.resource;

import ch.unige.events.event.dto.EventDTO;
import ch.unige.events.event.service.FeaturedService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/admin/events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
public class AdminEventResource {

    @Inject FeaturedService featuredService;

    @PATCH
    @Path("/{id}/feature")
    @RolesAllowed("ADMIN")
    public Response feature(@PathParam("id") Long id) {
        EventDTO dto = featuredService.feature(id);
        return Response.ok(dto).build();
    }

    @PATCH
    @Path("/{id}/unfeature")
    @RolesAllowed("ADMIN")
    public Response unfeature(@PathParam("id") Long id) {
        EventDTO dto = featuredService.unfeature(id);
        return Response.ok(dto).build();
    }
}
