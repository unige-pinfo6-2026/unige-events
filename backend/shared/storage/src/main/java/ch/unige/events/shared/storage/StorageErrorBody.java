package ch.unige.events.shared.storage;

/**
 * {@code {error, message}} response envelope wire-compatible with each
 * microservice's {@code ApiErrorResponse} record. Lives in the shared lib
 * so the storage exception mappers don't have to know which service's DTO
 * package they're running in.
 */
public record StorageErrorBody(String error, String message) {}
