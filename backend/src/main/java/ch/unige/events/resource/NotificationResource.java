package ch.unige.events.resource;

import ch.unige.events.dto.notification.NotificationDTO;
import ch.unige.events.service.NotificationService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/notifications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class NotificationResource {

    @Inject
    NotificationService notificationService;

    @Inject
    SecurityIdentity identity;

    @GET
    public List<NotificationDTO> getNotifications(
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
        String auth0Id = identity.getPrincipal().getName();
        return notificationService.getNotifications(auth0Id, page, size);
    }

    @PUT
    @Path("/{id}/read")
    public Response markRead(@PathParam("id") Long id) {
        String auth0Id = identity.getPrincipal().getName();
        NotificationDTO dto = notificationService.markRead(id, auth0Id);
        return Response.ok(dto).build();
    }

    @PUT
    @Path("/read-all")
    public Response markAllRead() {
        String auth0Id = identity.getPrincipal().getName();
        notificationService.markAllRead(auth0Id);
        return Response.noContent().build();
    }
}
