package ch.unige.events.user.resource;

import ch.unige.events.shared.domain.dto.AttendeeProjection;
import ch.unige.events.shared.jaxrs.Internal;
import ch.unige.events.user.entity.User;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.UUID;

/**
 * Internal endpoint feeding the attendees-list privacy filter consumed by
 * engagement-service's {@code AttendanceService.getAttendees}.
 *
 * <p>Returns one {@link AttendeeProjection} per existing id, regardless of
 * the target user's {@code profilePublic} flag — the consumer decides
 * whether to anonymize each row based on caller role
 * (creator / co-organizer / admin keep real identities; other authenticated
 * callers see private profiles as {@code displayName = null}).
 *
 * <p>Bypasses the ISSUE-93 anti-oracle by design: the caller is a trusted
 * sibling service (gated by the cluster perimeter + {@code X-Internal-Token}
 * via {@link Internal}), not the browser-facing public endpoint
 * {@code GET /users/{id}}. Missing ids are silently dropped so a bulk call
 * never returns 404; deleted users surface as "no projection" which the
 * consumer renders anonymously.
 *
 * <p>Cf. {@code backend/docs/internal-endpoints.md} entry #7.
 */
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
public class UserAttendeeProjectionInternalResource {

    @GET
    @Path("/_internal-attendee-projections")
    @PermitAll
    @Internal
    public List<AttendeeProjection> getAttendeeProjections(@QueryParam("ids") List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return User.<User>list("id in ?1", ids).stream()
                .map(u -> new AttendeeProjection(u.id, u.displayName, u.avatarUrl, u.profilePublic))
                .toList();
    }
}
