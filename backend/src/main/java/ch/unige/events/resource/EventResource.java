package ch.unige.events.resource;

import ch.unige.events.entity.Event;
import ch.unige.events.service.EventService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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
    public List<Event> getAll() {
        return eventService.getAll();
    }

    @POST
    @Transactional
    public Response create(Event event) {
        Event created = eventService.create(event);
        return Response.status(Response.Status.CREATED)
                .entity(created)
                .build();
    }
}
