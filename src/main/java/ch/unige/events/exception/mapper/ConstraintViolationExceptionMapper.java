package ch.unige.events.exception.mapper;

import ch.unige.events.dto.ValidationErrorDetail;
import ch.unige.events.dto.ValidationErrorResponse;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;

@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        List<ValidationErrorDetail> details = exception.getConstraintViolations()
            .stream()
            .map(violation -> new ValidationErrorDetail(violation.getPropertyPath().toString(), violation.getMessage()))
            .toList();

        return Response.status(Response.Status.BAD_REQUEST)
            .type(MediaType.APPLICATION_JSON)
            .entity(new ValidationErrorResponse(
                "validation_error",
                "Request validation failed",
                details
            ))
            .build();
    }
}
