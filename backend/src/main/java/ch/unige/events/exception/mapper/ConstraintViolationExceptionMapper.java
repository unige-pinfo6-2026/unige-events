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
            .map(violation -> new ValidationErrorDetail(
                    publicFieldName(violation.getPropertyPath().toString()),
                    violation.getMessage()))
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

    /**
     * Strips the property path down to its last segment so the response
     * doesn't leak the JAX-RS handler name and DTO parameter name (e.g.
     * {@code "create.request.websiteUrl"} → {@code "websiteUrl"}).
     *
     * <p>Pentest 2026-04-17 finding 4.20 — the leading segments are an
     * implementation detail of the resource layer that doesn't help the
     * frontend (which only knows the public field name) and gives an
     * attacker free reconnaissance of method/parameter names.
     */
    private static String publicFieldName(String propertyPath) {
        if (propertyPath == null) {
            return null;
        }
        int lastDot = propertyPath.lastIndexOf('.');
        return lastDot < 0 ? propertyPath : propertyPath.substring(lastDot + 1);
    }
}
