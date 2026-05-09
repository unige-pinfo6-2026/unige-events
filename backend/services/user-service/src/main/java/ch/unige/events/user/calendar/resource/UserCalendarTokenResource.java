package ch.unige.events.user.calendar.resource;

import ch.unige.events.user.calendar.dto.CalendarTokenResponse;
import ch.unige.events.user.calendar.service.CalendarService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * {@code GET /api/users/me/calendar-token} — returns (or lazily creates)
 * the user's stable calendar token + the matching ICS / webcal URLs.
 * {@code POST /api/users/me/calendar-token/regenerate} — rotates it.
 *
 * <p>In the legacy monolith these handlers lived on {@code UserResource} ;
 * in the microservices topology they ship with calendar-service so token
 * rotation stays co-located with the feed it secures.
 */
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
public class UserCalendarTokenResource {

    private final CalendarService calendarService;
    private final SecurityIdentity identity;

    @Inject
    public UserCalendarTokenResource(CalendarService calendarService, SecurityIdentity identity) {
        this.calendarService = calendarService;
        this.identity = identity;
    }

    @GET
    @Path("/me/calendar-token")
    @Authenticated
    public CalendarTokenResponse getMyCalendarToken() {
        return calendarService.getOrCreateToken(identity.getPrincipal().getName());
    }

    @POST
    @Path("/me/calendar-token/regenerate")
    @Authenticated
    @Consumes(MediaType.WILDCARD)
    public CalendarTokenResponse regenerateMyCalendarToken() {
        return calendarService.regenerateToken(identity.getPrincipal().getName());
    }
}
