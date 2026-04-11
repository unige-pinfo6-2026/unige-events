package ch.unige.events.resource;

import ch.unige.events.dto.event.CreateEventRequest;
import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.dto.event.UpdateEventRequest;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.Faculty;
import ch.unige.events.service.EventService;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.quarkus.security.identity.SecurityIdentity;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Path("/events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EventResource {

    private final EventService eventService;
    private final SecurityIdentity identity;

    @Inject
    public EventResource(EventService eventService, SecurityIdentity identity) {
        this.eventService = eventService;
        this.identity = identity;
    }

    @GET
    @PermitAll
    public List<EventDTO> getAll(
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size,
            @QueryParam("status") EventStatus status,
            @QueryParam("category") EventCategory category,
            @QueryParam("organizerId") UUID organizerId,
            @QueryParam("endDateFrom") LocalDateTime endDateFrom,
            @QueryParam("faculties") List<Faculty> faculties) {
        return eventService.getAll(page, size, status, category, organizerId, endDateFrom, faculties);
    }

    @POST
    @Authenticated
    public Response create(@Valid CreateEventRequest request) {
        String auth0Id = identity.getPrincipal().getName();
        EventDTO created = eventService.create(auth0Id, request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @GET
    @Path("/{id}")
    @PermitAll
    public Response getById(@PathParam("id") Long id) {
        EventDTO event = eventService.getById(id);
        return Response.ok(event).build();
    }

    @PUT
    @Path("/{id}")
    @Authenticated
    public Response update(@PathParam("id") Long id, @Valid UpdateEventRequest request) {
        String auth0Id = identity.getPrincipal().getName();
        EventDTO updated = eventService.update(id, auth0Id, request);
        return Response.ok(updated).build();
    }

    @DELETE
    @Path("/{id}")
    @Authenticated
    public Response delete(@PathParam("id") Long id) {
        String auth0Id = identity.getPrincipal().getName();
        eventService.delete(id, auth0Id);
        return Response.noContent().build();
    }

    @PATCH
    @Path("/{id}/publish")
    @RolesAllowed({"ORGANIZER", "ADMIN"})
    public Response publish(@PathParam("id") Long id) {
        String auth0Id = identity.getPrincipal().getName();
        boolean isAdmin = identity.hasRole("ADMIN");
        EventDTO published = eventService.publish(id, auth0Id, isAdmin);
        return Response.ok(published).build();
    }

    @POST
    @Path("/{id}/image")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Authenticated
    public Response uploadImage(@PathParam("id") Long id, @RestForm("file") FileUpload file) {
        String auth0Id = identity.getPrincipal().getName();
        boolean isAdmin = identity.hasRole("ADMIN");
        EventDTO updated = eventService.uploadImage(id, auth0Id, file, isAdmin);
        return Response.ok(updated).build();
    }
}
