package ch.unige.events.resource;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/hello")
public class ExampleResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "Hello from Quarkus REST";
    }

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    public String post(String test) {
        return test + " POSTED ! Hello from Quarkus REST";
    }
}
