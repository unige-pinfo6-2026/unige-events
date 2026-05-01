package ch.unige.events.resource;

import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.service.FeaturedService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/admin/events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminEventResource {

    @Inject
    FeaturedService featuredService;

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
