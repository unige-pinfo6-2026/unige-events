package ch.unige.events.resource;

import ch.unige.events.dto.ValidationErrorDetail;
import ch.unige.events.dto.ValidationErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;

@Provider
public class BadRequestExceptionMapper implements ExceptionMapper<BadRequestException> {

    @Override
    public Response toResponse(BadRequestException exception) {
        String detail = extractDetail(exception);
        return Response.status(Response.Status.BAD_REQUEST)
            .type(MediaType.APPLICATION_JSON)
            .entity(new ValidationErrorResponse(
                "validation_error",
                "Request validation failed",
                List.of(new ValidationErrorDetail(null, detail))
            ))
            .build();
    }

    private String extractDetail(BadRequestException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof JsonProcessingException jsonProcessingException) {
            return jsonProcessingException.getOriginalMessage();
        }
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return cause.getMessage();
        }
        if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
            return exception.getMessage();
        }
        return "Invalid request payload.";
    }
}
