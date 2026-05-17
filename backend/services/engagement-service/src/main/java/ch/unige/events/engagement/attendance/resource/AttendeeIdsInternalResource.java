package ch.unige.events.engagement.attendance.resource;

import ch.unige.events.shared.domain.enums.AttendanceStatus;
import ch.unige.events.shared.jaxrs.Internal;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.UUID;

/**
 * Internal endpoint exposing the {@code userId} list of attendees for an
 * event filtered by status. Primary consumer (SCRUM-99) :
 * notification-service ({@code EventCancelledConsumer} and
 * {@code EventUpdatedConsumer}) iterates on the result to fan out
 * EVENT_CANCELLED / EVENT_UPDATED notifications to every {@code ATTENDING}
 * user.
 *
 * <p>Gated by {@link Internal} : {@link ch.unige.events.shared.jaxrs.InternalTokenFilter}
 * rejects every request without a valid {@code X-Internal-Token} with 404.
 * Not in {@code openapi.yaml} ; documented in
 * {@code backend/docs/internal-endpoints.md} entry #8.
 *
 * <p>Unknown {@code eventId} returns {@code 200} with an empty list — no
 * oracle on event existence (the {@code GET /events/{id}} public endpoint
 * already gates that via the ISSUE-92 anti-oracle).
 */
@Path("/events")
@Produces(MediaType.APPLICATION_JSON)
public class AttendeeIdsInternalResource {

    private final EntityManager entityManager;

    @Inject
    public AttendeeIdsInternalResource(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @GET
    @Path("/{eventId}/_internal-attendee-ids")
    @PermitAll
    @Internal
    public List<UUID> getAttendeeIds(@PathParam("eventId") Long eventId,
                                     @QueryParam("status") @DefaultValue("ATTENDING") AttendanceStatus status) {
        // JPQL projection — avoid loading the full Attendance row to keep
        // the fan-out payload light (only userId is needed).
        return entityManager.createQuery(
                "SELECT a.userId FROM Attendance a WHERE a.eventId = :eventId AND a.status = :status",
                UUID.class)
                .setParameter("eventId", eventId)
                .setParameter("status", status)
                .getResultList();
    }
}
