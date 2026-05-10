package ch.unige.events.shared.client;

import ch.unige.events.shared.domain.dto.AttendanceDTO;
import ch.unige.events.shared.domain.dto.AttendanceSummary;
import ch.unige.events.shared.tracing.RequestIdClientFilter;

import io.quarkus.logging.Log;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
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
}
