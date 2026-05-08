package ch.unige.events.scheduler;

import ch.unige.events.service.EventExpirationService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
class EventExpirationJobTest {

    @InjectMock
    EventExpirationService expirationService;

    @Inject
    EventExpirationJob job;

    @Test
    void runExpiration_delegatesToService() {
        job.runExpiration();

        Mockito.verify(expirationService, Mockito.times(1)).expireEvents();
    }
}
