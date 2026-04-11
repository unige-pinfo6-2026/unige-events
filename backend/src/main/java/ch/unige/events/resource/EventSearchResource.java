package ch.unige.events.resource;

import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.Faculty;
import ch.unige.events.service.EventSearchService;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.time.LocalDate;
import java.util.List;

@Path("/events/search")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EventSearchResource {

    private final EventSearchService eventSearchService;

    @Inject
    public EventSearchResource(EventSearchService eventSearchService) {
        this.eventSearchService = eventSearchService;
    }

    @GET
    @PermitAll
    public List<EventDTO> search(
            @QueryParam("q") String q,
            @QueryParam("category") EventCategory category,
            @QueryParam("faculties") List<Faculty> faculties,
            @QueryParam("dateFrom") LocalDate dateFrom,
            @QueryParam("dateTo") LocalDate dateTo,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
        return eventSearchService.search(q, category, faculties, dateFrom, dateTo, page, size);
    }
}
