package ch.unige.events.user.exception.mapper;

import ch.unige.events.user.dto.ApiErrorResponse;
import ch.unige.events.user.exception.FileTooLargeException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class FileTooLargeExceptionMapper implements ExceptionMapper<FileTooLargeException> {

    @Override
    public Response toResponse(FileTooLargeException exception) {
        return Response.status(Response.Status.REQUEST_ENTITY_TOO_LARGE)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ApiErrorResponse("payload_too_large", exception.getMessage()))
                .build();
    }
}
