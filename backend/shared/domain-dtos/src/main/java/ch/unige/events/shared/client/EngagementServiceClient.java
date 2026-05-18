package ch.unige.events.shared.client;

import ch.unige.events.shared.domain.dto.AttendanceDTO;
import ch.unige.events.shared.domain.dto.AttendanceSummary;
import ch.unige.events.shared.domain.dto.CommentVisibilityProjection;
import ch.unige.events.shared.tracing.RequestIdClientFilter;

import io.quarkus.logging.Log;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST client for cross-service reads of Attendance entities.
 *
 * <p>Producer: engagement-service (host of the {@code attendances} table
 * post-Étape 2.1.1 rename, plus the {@code comments} table post-2.4.1).
 *
 * <p>Consumers: event-service (capacity gating + stats), user-service
 * (calendar ICS feed enrichment).
 *
 * <p>URL: configured per-consumer via
 * {@code quarkus.rest-client.engagement-service.url=
 *  ${ENGAGEMENT_SERVICE_URL:http://engagement-service:8080}}.
 */
@RegisterRestClient(configKey = "engagement-service")
@RegisterProvider(RequestIdClientFilter.class)
@RegisterProvider(ch.unige.events.shared.tracing.InternalTokenClientFilter.class)
public interface EngagementServiceClient {

    /**
     * Returns count-by-status (ATTENDING + WAITLISTED) for a given event,
     * used by event-service for capacity gating without pulling rows.
     */
    @GET
    @Path("/events/{eventId}/attendance-summary")
    @Retry(maxRetries = 3, delay = 200, delayUnit = ChronoUnit.MILLIS)
    @Timeout(value = 2, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(failureRatio = 0.5, requestVolumeThreshold = 10)
    @Fallback(fallbackMethod = "getAttendanceSummaryFallback")
    AttendanceSummary getAttendanceSummary(@PathParam("eventId") long eventId);

    default AttendanceSummary getAttendanceSummaryFallback(long eventId) {
        Log.warnf("[REST_FALLBACK_engagement-service] getAttendanceSummary(%d) — returning AttendanceSummary.of(0, 0) (counts will display as 0)", eventId);
        return AttendanceSummary.of(0L, 0L);
    }

    /**
     * Returns the user's attendances filtered by status — used by
     * user-service to project an ICS feed.
     */
    @GET
    @Path("/users/{id}/attendances")
    @Retry(maxRetries = 3, delay = 200, delayUnit = ChronoUnit.MILLIS)
    @Timeout(value = 2, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(failureRatio = 0.5, requestVolumeThreshold = 10)
    @Fallback(fallbackMethod = "getUserAttendancesFallback")
    List<AttendanceDTO> getUserAttendances(@PathParam("id") UUID id,
                                            @QueryParam("status") String status);

    default List<AttendanceDTO> getUserAttendancesFallback(UUID id, String status) {
        Log.warnf("[REST_FALLBACK_engagement-service] getUserAttendances(%s, status=%s) — returning empty list (downstream unavailable, ICS feed degraded)", id, status);
        return List.of();
    }

    /**
     * Bulk attendance summary lookup for a list of event ids — Décision I
     * of finalization-ultimate spec. Replaces the legacy
     * {@code AttendanceStub.countGroupedByStatus(ids, ...)} pattern that
     * required cross-service entity navigation. Provider:
     * {@code GET /events/_bulk-attendance-summary?ids=42&ids=7} on
     * engagement-service. The {@code _bulk-} prefix avoids ambiguity with
     * the path-param route {@code /events/{eventId}/attendance-summary}.
     */
    @GET
    @Path("/events/_bulk-attendance-summary")
    @Retry(maxRetries = 3, delay = 200, delayUnit = ChronoUnit.MILLIS)
    @Timeout(value = 2, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(failureRatio = 0.5, requestVolumeThreshold = 10)
    @Fallback(fallbackMethod = "getAttendanceSummariesBulkFallback")
    Map<Long, AttendanceSummary> getAttendanceSummariesBulk(@QueryParam("ids") List<Long> ids);

    default Map<Long, AttendanceSummary> getAttendanceSummariesBulkFallback(List<Long> ids) {
        Log.warnf("[REST_FALLBACK_engagement-service] getAttendanceSummariesBulk(ids=%d) — returning empty map (counts will display as 0)", ids.size());
        return Map.of();
    }

    /**
     * Internal endpoint (SCRUM-99) consumed by notification-service to
     * resolve the recipient list of an event-lifecycle notification. Returns
     * the {@code List<UUID>} of users with the given attendance status on
     * the event. Provider :
     * {@code GET /events/{eventId}/_internal-attendee-ids?status=ATTENDING}
     * on engagement-service, gated {@code @Internal} (X-Internal-Token).
     * Not in {@code openapi.yaml} ; documented in
     * {@code backend/docs/internal-endpoints.md} entry #8.
     *
     * <p>Fallback : empty list. The consumer logs a warning ; the
     * notification is silently skipped (acceptable degradation — Kafka
     * re-delivery on the next event will fan it out if engagement-service
     * comes back).
     */
    @GET
    @Path("/events/{eventId}/_internal-attendee-ids")
    @Retry(maxRetries = 3, delay = 200, delayUnit = ChronoUnit.MILLIS)
    @Timeout(value = 2, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(failureRatio = 0.5, requestVolumeThreshold = 10)
    @Fallback(fallbackMethod = "getAttendeeIdsFallback")
    List<UUID> getAttendeeIds(@PathParam("eventId") Long eventId,
                              @QueryParam("status") String status);

    default List<UUID> getAttendeeIdsFallback(Long eventId, String status) {
        Log.warnf("[REST_FALLBACK_engagement-service] getAttendeeIds(event=%d, status=%s) — returning empty list (downstream unavailable, notifications skipped)", eventId, status);
        return List.of();
    }

    /**
     * Internal endpoint (SCRUM-144 — Décision L) consumed by moderation-service
     * to validate that the caller can see a given comment before persisting a
     * {@code POST /comments/{commentId}/report}. The cascade ISSUE-92 +
     * SCRUM-136 is enforced server-side inside engagement-service via
     * {@code EventServiceClient.getByIdWithCoOrgCheck} on the parent event.
     *
     * <p>Provider :
     * {@code GET /comments/{commentId}/_internal-visibility?callerId={uuid}}
     * on engagement-service, gated {@code @Internal} (X-Internal-Token). Not
     * in {@code openapi.yaml} — documented in
     * {@code backend/docs/internal-endpoints.md} entry #10.
     *
     * <p>Resilience tuned for the report-comment flow :
     * <ul>
     *   <li>{@code abortOn = NotFoundException} : a legitimate 404 (comment
     *       inexistant OR event invisible OR bad token) must short-circuit the
     *       retry — otherwise a wave of reports against invisible comments
     *       would trip the CB.</li>
     *   <li>{@code skipOn = NotFoundException} : same reasoning for the CB
     *       volume threshold.</li>
     *   <li>{@code @Fallback} throws 503 on infra failure so the caller (the
     *       resource layer of moderation-service) surfaces a meaningful
     *       error to the browser instead of silently dropping the report.</li>
     * </ul>
     */
    @GET
    @Path("/comments/{commentId}/_internal-visibility")
    @Retry(maxRetries = 3, delay = 200, delayUnit = ChronoUnit.MILLIS, abortOn = NotFoundException.class)
    @Timeout(value = 2, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(failureRatio = 0.5, requestVolumeThreshold = 10, skipOn = NotFoundException.class)
    @Fallback(fallbackMethod = "getCommentVisibilityFallback", skipOn = NotFoundException.class)
    CommentVisibilityProjection getCommentVisibility(@PathParam("commentId") Long commentId,
                                                     @QueryParam("callerId") UUID callerId);

    default CommentVisibilityProjection getCommentVisibilityFallback(Long commentId, UUID callerId) {
        Log.errorf("[REST_FALLBACK_engagement-service] getCommentVisibility(comment=%d, caller=%s) — engagement-service unavailable ; surfacing as 503 to caller",
                commentId, callerId);
        throw new WebApplicationException(
                "Comment visibility check unavailable",
                Response.Status.SERVICE_UNAVAILABLE);
    }
}
