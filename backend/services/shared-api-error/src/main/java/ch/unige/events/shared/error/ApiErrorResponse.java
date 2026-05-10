package ch.unige.events.shared.error;

import java.util.Objects;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Canonical error envelope returned by every microservice. Two fields:
 * a short machine-readable {@code error} code (e.g. {@code "conflict"},
 * {@code "validation_failed"}) and a human-readable {@code message}.
 */
@Schema(name = "ApiErrorResponse",
        description = "Canonical error envelope returned by every microservice.")
public record ApiErrorResponse(
        @Schema(description = "Error code (machine-readable, lowercase snake_case)") String error,
        @Schema(description = "Human-readable message") String message) {

    public ApiErrorResponse {
        Objects.requireNonNull(error, "error code must not be null");
        if (error.isBlank()) {
            throw new IllegalArgumentException("error code must not be blank");
        }
    }
}
