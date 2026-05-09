package ch.unige.events.shared.client;

import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.tracing.RequestIdClientFilter;

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
import java.util.UUID;

/**
 * REST client for cross-service reads of Event entities.
 *
 * <p>Producer: event-service (Étapes 4.1, 4.4 — also exposes the
 * {@code ?check-co-org-of=&lt;UUID&gt;} param that centralizes the
 * SCRUM-136 cascade and the {@code ?ids=} bulk lookup).
 *
 * <p>Consumers: engagement-service, moderation-service, user-service.
 *
 * <p>URL: configured per-consumer via
 * {@code quarkus.rest-client.event-service.url=
 *  ${EVENT_SERVICE_URL:http://event-service:8080}}.
 *
 * <p>Resilience: standard 3-retry / 2 s timeout / 50 % CB ratio
 * (Décision C of finalization spec); fallback returns null /
 * {@link List#of()} so the call-site can choose between propagating a
 * 404 ({@link jakarta.ws.rs.NotFoundException}) or treating the absence
 * as a degraded read.
 */
@RegisterRestClient(configKey = "event-service")
@RegisterProvider(RequestIdClientFilter.class)
@Path("/events")
public interface EventServiceClient {

    @GET
    @Path("/{id}")
    @Retry(maxRetries = 3, delay = 200, delayUnit = ChronoUnit.MILLIS)
    @Timeout(value = 2, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(failureRatio = 0.5, requestVolumeThreshold = 10)
    @Fallback(fallbackMethod = "getByIdFallback")
    EventDTO getById(@PathParam("id") long id);

    default EventDTO getByIdFallback(long id) {
        return null;
    }

    /**
     * Returns the event payload + a {@code coOrganizerOf: bool} field
     * indicating whether the given userId is the creator OR an ACCEPTED
     * co-organizer. Used to centralize cascade SCRUM-136 server-side
     * (post-2.2.4 the {@code event_co_organizers} table moved into
     * event-service).
     */
    @GET
    @Path("/{id}")
    @Retry(maxRetries = 3, delay = 200, delayUnit = ChronoUnit.MILLIS)
    @Timeout(value = 2, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(failureRatio = 0.5, requestVolumeThreshold = 10)
    @Fallback(fallbackMethod = "getByIdWithCoOrgCheckFallback")
    EventDTO getByIdWithCoOrgCheck(@PathParam("id") long id,
                                    @QueryParam("check-co-org-of") UUID userId);

    default EventDTO getByIdWithCoOrgCheckFallback(long id, UUID userId) {
        return null;
    }

    @GET
    @Retry(maxRetries = 3, delay = 200, delayUnit = ChronoUnit.MILLIS)
    @Timeout(value = 2, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(failureRatio = 0.5, requestVolumeThreshold = 10)
    @Fallback(fallbackMethod = "findByIdsFallback")
    List<EventDTO> findByIds(@QueryParam("ids") List<Long> ids,
                             @QueryParam("status") String status);

    default List<EventDTO> findByIdsFallback(List<Long> ids, String status) {
        return List.of();
    }
}
