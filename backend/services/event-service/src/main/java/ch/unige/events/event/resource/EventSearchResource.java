package ch.unige.events.event.resource;

import ch.unige.events.event.dto.EventDTO;
import ch.unige.events.event.entity.EventCategory;
import ch.unige.events.event.entity.Faculty;
import ch.unige.events.event.service.EventSearchService;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
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
    @SuppressWarnings("java:S107")
    public List<EventDTO> search(
            @QueryParam("q") String q,
            @QueryParam("category") EventCategory category,
            @QueryParam("faculty") Faculty faculty,
            @QueryParam("facultyNone") Boolean facultyNone,
            @QueryParam("tags") List<String> tags,
            @QueryParam("dateFrom") LocalDate dateFrom,
            @QueryParam("dateTo") LocalDate dateTo,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
        return eventSearchService.search(q, category, faculty, facultyNone, tags, dateFrom, dateTo, page, size);
    }
}
