package ch.unige.events.resource;

import ch.unige.events.dto.CreateEventRequest;
import ch.unige.events.dto.EventDTO;
import ch.unige.events.service.EventService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EventResource {

    @Inject
    EventService eventService;

    @GET
    public List<EventDTO> getAll() {
        return eventService.getAll();
    }

    @POST
    public Response create(@Valid CreateEventRequest request) {
        EventDTO created = eventService.create(request);
        return Response.status(Response.Status.CREATED)
                .entity(created)
                .build();
    }
}
