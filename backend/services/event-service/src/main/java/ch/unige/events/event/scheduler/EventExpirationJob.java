package ch.unige.events.event.scheduler;

import ch.unige.events.event.service.EventExpirationService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class EventExpirationJob {

    @Inject EventExpirationService expirationService;

    @Scheduled(every = "1h")
    public void runExpiration() {
        expirationService.expireEvents();
    }
}
