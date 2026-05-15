package ch.unige.events.event.view.dto;

import java.util.UUID;

/**
 * Optional body for {@code POST /events/{id}/view}. Anonymous callers send a
 * client-generated UUID (persisted in browser localStorage) so the backend can
 * de-duplicate views per {@code (eventId, sessionId)}. Ignored when the caller
 * is authenticated — the identity-derived userId takes priority.
 */
public record RecordViewRequest(UUID sessionId) {}
