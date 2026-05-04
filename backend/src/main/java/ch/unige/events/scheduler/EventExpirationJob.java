package ch.unige.events.scheduler;

import ch.unige.events.service.EventExpirationService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class EventExpirationJob {

    @Inject
    EventExpirationService expirationService;

    @Scheduled(every = "1h")
    public void runExpiration() {
        expirationService.expireEvents();
    }
}
