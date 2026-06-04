package ch.unige.events.event.service;

import ch.unige.events.event.dto.CreateEventRequest;
import ch.unige.events.event.entity.Event;
import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.projections.CallerIdentity;

import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Hand-wired coverage for two {@link EventService} branches that are
 * unreachable through the CDI-injected runtime:
 *
 * <ul>
 *   <li>L215/216 — the {@code persistParent} {@code requireUuid() == null}
 *       guard. The real {@link CallerIdentity#requireUuid()} <em>throws</em>
 *       rather than returning null, so the {@code null} never crosses into
 *       the comparison. Wired by hand with a mocked {@link CallerIdentity}
 *       returning null.</li>
 *   <li>L635/636-642 — the duplicate title-collision cap (n > 100 → 422). A
 *       real DB cannot be made to collide on 100 candidate titles cheaply;
 *       {@link PanacheMock} stubs {@code Event.count(...)} to always report a
 *       collision so the suffix loop runs past 100.</li>
 * </ul>
 *
 * <p>Annotated {@code @QuarkusTest} so the executed service bytecode is
 * captured by quarkus-jacoco.
 */
@QuarkusTest
class EventServiceHandWiredTest {

    private EventService service;
    private CallerIdentity callerIdentity;

    private CreateEventRequest req(String title) {
        LocalDateTime start = LocalDateTime.of(2999, 1, 1, 0, 0).plusDays(2);
        return new CreateEventRequest(
                title, "desc", "loc", start, start.plusHours(2),
                EventCategory.ACADEMIC, null, null,
                null, null, null, null, null,
                null, null, null);
    }

    @Test
    void create_requireUuidNull_throws404() {
        // Hand-wire a service whose CallerIdentity yields a null UUID. The
        // persistParent guard rejects with 404 before any persist().
        service = new EventService();
        callerIdentity = mock(CallerIdentity.class);
        when(callerIdentity.requireUuid()).thenReturn(null);
        service.callerIdentity = callerIdentity;

        assertThrows(NotFoundException.class,
                () -> service.create("auth0|x", req("orphan")));
    }

    @Test
    void duplicate_titleCollisionCapExceeded_throws422() {
        // Stub Event.findByIdOptional + Event.count so every candidate title
        // collides → the suffix loop passes 100 and surfaces 422.
        service = new EventService();
        callerIdentity = mock(CallerIdentity.class);
        when(callerIdentity.requireUuid()).thenReturn(UUID.randomUUID());
        service.callerIdentity = callerIdentity;

        PanacheMock.mock(Event.class);
        Event source = new Event();
        source.id = 1L;
        source.title = "Always Colliding";
        source.status = EventStatus.PUBLISHED;
        when(Event.findByIdOptional(1L)).thenReturn(Optional.of(source));
        // Every existence check reports a collision → the loop never settles.
        // Panache count(String, Object...) is varargs → match with a single
        // Object[] matcher (per-element any() trips Mockito InvalidUseOfMatchers
        // on varargs; this mirrors the ReportServiceTest convention).
        when(Event.count(anyString(), any(Object[].class))).thenReturn(1L);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.duplicate(1L, "auth0|admin", true));
        assertEquals(422, ex.getResponse().getStatus());
    }
}
