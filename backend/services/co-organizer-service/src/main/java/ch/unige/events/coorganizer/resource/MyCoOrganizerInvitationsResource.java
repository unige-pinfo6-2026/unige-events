package ch.unige.events.coorganizer.resource;

import ch.unige.events.coorganizer.dto.CoOrganizerInvitationDTO;
import ch.unige.events.coorganizer.entity.CoOrganizerStatus;
import ch.unige.events.coorganizer.service.EventCoOrganizerService;

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
 * {@code GET /api/users/me/co-organizer-invitations} — paginated list of
 * the caller's pending (or filtered-by-status) co-organizer invitations.
 * Lived on UserResource in legacy ; co-located with co-organizer-service
 * in the microservices topology.
 */
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
public class MyCoOrganizerInvitationsResource {

    private final EventCoOrganizerService service;
    private final SecurityIdentity identity;

    @Inject
    public MyCoOrganizerInvitationsResource(EventCoOrganizerService service, SecurityIdentity identity) {
        this.service = service;
        this.identity = identity;
    }

    @GET
    @Path("/me/co-organizer-invitations")
    @Authenticated
    public List<CoOrganizerInvitationDTO> getMyCoOrganizerInvitations(
            @QueryParam("status") CoOrganizerStatus status,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
        return service.getMyInvitations(
                identity.getPrincipal().getName(), status, page, size);
    }
}
