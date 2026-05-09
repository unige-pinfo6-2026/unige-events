package ch.unige.events.shared.error;

/**
 * Canonical error envelope returned by every microservice. Two fields:
 * a short machine-readable {@code error} code (e.g. {@code "conflict"},
 * {@code "validation_failed"}) and a human-readable {@code message}.
 */
public record ApiErrorResponse(String error, String message) {
}
