package ch.unige.events.event.me.resource;

import ch.unige.events.event.me.dto.EventDTO;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.event.me.service.MyEventsService;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;

import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * {@code GET /api/users/me/events} — paginated list of the events the
 * caller has created. Lived on UserResource in legacy ; in the
 * microservices topology it ships as the first BFF endpoint of
 * event-service (me-aggregator-service absorbed post-finalization).
 *
 * <p>The other /me/* paths (favorites, attendances, participations) stay
 * on their owning services until event-service ships and the BFF can
 * fan-out via REST clients.
 */
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
public class MyEventsResource {

    private final MyEventsService myEventsService;
    private final SecurityIdentity identity;

    @Inject
    public MyEventsResource(MyEventsService myEventsService, SecurityIdentity identity) {
        this.myEventsService = myEventsService;
        this.identity = identity;
    }

    @GET
    @Path("/me/events")
    @Authenticated
    public List<EventDTO> getMyEvents(
            @QueryParam("status") EventStatus status,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
        return myEventsService.getMyEvents(identity.getPrincipal().getName(), status, page, size);
    }
}
