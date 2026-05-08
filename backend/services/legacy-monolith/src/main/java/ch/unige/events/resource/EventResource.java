package ch.unige.events.resource;

import ch.unige.events.config.PerUserRateLimit;
import ch.unige.events.dto.ApiErrorResponse;
import ch.unige.events.dto.event.CreateEventRequest;
import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.dto.event.UpdateEventRequest;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.Faculty;
import ch.unige.events.service.EventService;
import ch.unige.events.service.FeaturedService;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.PermitAll;
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
    FeaturedService featuredService;

    @Inject
    public EventResource(EventService eventService, SecurityIdentity identity) {
        this.eventService = eventService;
        this.identity = identity;
    }

    @GET
    @Path("/featured")
    @PermitAll
    public List<EventDTO> getFeatured(
            @QueryParam("limit") @DefaultValue("6") @Min(1) @Max(12) int limit) {
        return featuredService.getFeatured(limit);
    }

    @GET
    @PermitAll
    @SuppressWarnings("java:S107") // Filter-heavy list endpoint — flat params match the REST query signature 1:1.
    public List<EventDTO> getAll(
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size,
            @QueryParam("status") EventStatus status,
            @QueryParam("category") EventCategory category,
            @QueryParam("organizerId") UUID organizerId,
            @QueryParam("endDateFrom") LocalDateTime endDateFrom,
            @QueryParam("faculty") Faculty faculty,
            @QueryParam("facultyNone") Boolean facultyNone,
            @QueryParam("featured") Boolean featured) {
        EventStatus effectiveStatus = status;
        if (organizerId != null) {
            if (effectiveStatus == null) {
                effectiveStatus = EventStatus.PUBLISHED;
            } else if (effectiveStatus != EventStatus.PUBLISHED) {
                throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ApiErrorResponse(
                            "organizer_filter_requires_published",
                            "Filtering by organizerId is only allowed for status=PUBLISHED. Use GET /users/me/events for your own events."))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
            }
        }
        return eventService.getAll(page, size, effectiveStatus, category, organizerId, endDateFrom, faculty, facultyNone, featured);
    }

    @POST
    @Authenticated
    @PerUserRateLimit(name = "events.create", max = 10)
    public Response create(@Valid CreateEventRequest request) {
        String auth0Id = identity.getPrincipal().getName();
        EventDTO created = eventService.create(auth0Id, request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @GET
    @Path("/{id}")
    @PermitAll
    public Response getById(@PathParam("id") Long id) {
        String auth0Id = identity.isAnonymous() ? null : identity.getPrincipal().getName();
        boolean isAdmin = !identity.isAnonymous() && identity.hasRole("ADMIN");
        EventDTO event = eventService.getById(id, auth0Id, isAdmin);
        return Response.ok(event).build();
    }

    @GET
    @Path("/{id}/occurrences")
    @PermitAll
    public List<EventDTO> getOccurrences(
            @PathParam("id") Long id,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("52") @Positive @Max(52) int size) {
        String auth0Id = identity.isAnonymous() ? null : identity.getPrincipal().getName();
        boolean isAdmin = !identity.isAnonymous() && identity.hasRole("ADMIN");
        return eventService.getOccurrences(id, auth0Id, isAdmin, page, size);
    }

    @PUT
    @Path("/{id}")
    @Authenticated
    @PerUserRateLimit(name = "events.update", max = 10)
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
    @Path("/{id}/cancel")
    @Authenticated
    @PerUserRateLimit(name = "events.cancel", max = 10)
    public Response cancel(@PathParam("id") Long id) {
        String auth0Id = identity.getPrincipal().getName();
        EventDTO cancelled = eventService.cancel(id, auth0Id);
        return Response.ok(cancelled).build();
    }

    @PATCH
    @Path("/{id}/restore")
    @Authenticated
    @PerUserRateLimit(name = "events.restore", max = 10)
    public Response restore(@PathParam("id") Long id) {
        String auth0Id = identity.getPrincipal().getName();
        EventDTO restored = eventService.restore(id, auth0Id);
        return Response.ok(restored).build();
    }

    // Publish requires only @Authenticated — creator-or-admin enforcement lives in
    // EventService.publish(). Previously @RolesAllowed({"ORGANIZER","ADMIN"}) locked out
    // regular users from publishing their own drafts, even though the POST /events
    // endpoint lets any authenticated user create a PUBLISHED event directly.
    @PATCH
    @Path("/{id}/publish")
    @Authenticated
    @PerUserRateLimit(name = "events.publish", max = 10)
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
    @PerUserRateLimit(name = "events.uploadImage", max = 5)
    public Response uploadImage(@PathParam("id") Long id, @RestForm("file") FileUpload file) {
        if (file == null) {
            // Pentest 2026-04-17 finding 4.22 — wrong multipart field name
            // would otherwise fall through to a 500 from a downstream NPE.
            throw new BadRequestException("Missing required form field: file");
        }
        String auth0Id = identity.getPrincipal().getName();
        boolean isAdmin = identity.hasRole("ADMIN");
        EventDTO updated = eventService.uploadImage(id, auth0Id, file, isAdmin);
        return Response.ok(updated).build();
    }
}
