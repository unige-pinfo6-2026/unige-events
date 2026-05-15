package ch.unige.events.event.scheduler;

import ch.unige.events.event.service.EventExpirationService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Hourly cron that flips elapsed {@code PUBLISHED} events to
 * {@code EXPIRED} and emits {@code events.expired} on Kafka.
 *
 * <p><strong>Replicas constraint</strong> — the SmallRye
 * scheduler has no built-in leader election, so every pod that runs
 * {@code event-service} fires this cron independently. Running at
 * {@code replicas: 2+} would double-fire {@code events.expired} on every
 * candidate and race two transactions on the same {@code Event.status}
 * flip. The Helm chart enforces {@code replicas: 1 strict} for
 * {@code event-service} via a {@code {{- fail }}} guard in
 * {@code k8s/chart/templates/event-service/deployment.yaml}; see ADR-001
 * § "How this is enforced" point 6 for the rationale and the planned
 * Shedlock migration path. {@link EventExpirationService#expireEvents()}
 * also takes a {@code pg_advisory_xact_lock(0)} as defense-in-depth
 * against an in-cluster scale-up that bypasses {@code helm install}.
 */
@ApplicationScoped
public class EventExpirationJob {

    @Inject EventExpirationService expirationService;

    @Scheduled(every = "1h")
    public void runExpiration() {
        expirationService.expireEvents();
    }
}
