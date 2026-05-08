package ch.unige.events.resource;

import ch.unige.events.service.CalendarService;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@Path("/calendar")
public class CalendarResource {

    private final CalendarService calendarService;

    @Inject
    public CalendarResource(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @GET
    @Path("/{calendarToken}.ics")
    @PermitAll
    @Produces("text/calendar;charset=UTF-8")
    public Response getCalendarFeed(@PathParam("calendarToken") UUID calendarToken) {
        String icsContent = calendarService.generateIcsFeed(calendarToken);
        return Response.ok(icsContent)
                .header("Content-Disposition", "attachment; filename=\"unige-events.ics\"")
                .build();
    }
}
