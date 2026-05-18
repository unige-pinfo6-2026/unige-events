package ch.unige.events.notification.dto;

/**
 * Body of {@code PATCH /api/users/me/notifications/read-all}. Carries the
 * number of rows transitioned from {@code read=false} to {@code read=true}
 * by the call. Idempotent : {@code 0} on a fully-read inbox.
 */
public record ReadAllResponse(long updated) {
}
