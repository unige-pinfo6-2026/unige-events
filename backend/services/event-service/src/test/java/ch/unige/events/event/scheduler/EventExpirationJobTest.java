package ch.unige.events.event.scheduler;

import ch.unige.events.event.service.EventExpirationService;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class EventExpirationJobTest {

    @Inject EventExpirationJob job;

    @InjectMock EventExpirationService expirationService;

    @Test
    void runExpiration_delegatesToService() {
        when(expirationService.expireEvents()).thenReturn(0);
        job.runExpiration();
        verify(expirationService).expireEvents();
    }
}
