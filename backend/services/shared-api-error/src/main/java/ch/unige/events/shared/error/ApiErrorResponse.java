package ch.unige.events.shared.error;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Canonical error envelope returned by every microservice. Two fields:
 * a short machine-readable {@code error} code (e.g. {@code "conflict"},
 * {@code "validation_failed"}) and a human-readable {@code message}.
 */
@Schema(name = "ApiErrorResponse",
        description = "Canonical error envelope returned by every microservice.")
public record ApiErrorResponse(String error, String message) {
}
