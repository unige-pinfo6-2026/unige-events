package ch.unige.events.event.exception.mapper;

import ch.unige.events.event.dto.ApiErrorResponse;
import ch.unige.events.event.exception.InvalidFileTypeException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class InvalidFileTypeExceptionMapper implements ExceptionMapper<InvalidFileTypeException> {

    @Override
    public Response toResponse(InvalidFileTypeException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ApiErrorResponse("invalid_file_type", exception.getMessage()))
                .build();
    }
}
