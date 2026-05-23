package ch.unige.events.event.stats.service;

import ch.unige.events.event.entity.Event;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.domain.projections.CallerIdentity;

import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Coverage suite for the {@code callerUuid == null} guard of
 * {@link EventStatsService}'s private {@code isCreatorOrAcceptedCoOrganizer}
 * helper — the early {@code return false} that denies stats access when the
 * caller UUID could not be resolved.
 *
 * <p>This branch is unreachable through {@code getStats} with a real
 * {@link CallerIdentity}: an unresolved caller makes {@code requireUuid()}
 * throw {@link jakarta.ws.rs.NotFoundException} <em>before</em> the helper is
 * invoked, so the {@code null} never crosses into the comparison. The service
 * is therefore wired by hand with a mocked {@link CallerIdentity} whose
 * {@code requireUuid()} returns {@code null}, with {@link Event#findByIdOptional}
 * stubbed via {@link PanacheMock} so the event lookup succeeds and execution
 * reaches the guard. Annotated {@code @QuarkusTest} so the executed bytecode is
 * captured by quarkus-jacoco.
 */
@QuarkusTest
class EventStatsServiceCallerNullTest {

    private EventStatsService service;

    private final Long eventId = 5151L;

    @BeforeEach
    void setUp() {
        service = new EventStatsService();
        CallerIdentity callerIdentity = mock(CallerIdentity.class);
        when(callerIdentity.requireUuid()).thenReturn(null);
        service.callerIdentity = callerIdentity;
        service.engagementClient = mock(EngagementServiceClient.class);
    }

    @Test
    void getStats_callerUuidNull_deniedAsForbidden() {
        PanacheMock.mock(Event.class);
        Event event = new Event();
        event.creatorId = UUID.randomUUID();
        when(Event.findByIdOptional(eventId)).thenReturn(Optional.of(event));

        // callerUuid == null → isCreatorOrAcceptedCoOrganizer returns false →
        // the access guard rejects with 403.
        assertThrows(ForbiddenException.class, () -> service.getStats("auth0|x", eventId));
    }
}
