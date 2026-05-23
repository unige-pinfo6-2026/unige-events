package ch.unige.events.event.coorganizer.service;

import ch.unige.events.event.coorganizer.dto.CoOrganizerDTO;
import ch.unige.events.event.coorganizer.entity.EventCoOrganizer;
import ch.unige.events.event.entity.Event;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.enums.CoOrganizerStatus;

import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Coverage suite for the private {@code safeGetUser} {@code catch} blocks of
 * {@link EventCoOrganizerService} — the user-enrichment degradation paths that
 * absorb a {@link NotFoundException} (hard-deleted user) and any other
 * {@link RuntimeException} (infra failure: timeout, CB open, JSON parse) and
 * return {@code null} so the DTO renders an anonymous author rather than
 * propagating the failure.
 *
 * <p>These catches are <em>unreachable</em> through an {@code @InjectMock
 * @RestClient UserServiceClient}: the MicroProfile Fault Tolerance proxy that
 * owns the {@code @Fallback} on {@link UserServiceClient#getById} intercepts
 * the stubbed {@code thenThrow} and substitutes the fallback {@code null} — so
 * the throw never crosses into the service's own {@code try}. The service is
 * therefore wired by hand with a <em>plain</em> Mockito client (no FT proxy) so
 * the exception is real, while the Panache statics it calls along the way
 * ({@link Event#findByIdOptional} and {@link EventCoOrganizer#findByEvent}) are
 * stubbed via {@link PanacheMock}. Annotated {@code @QuarkusTest} so the
 * executed service bytecode is captured by quarkus-jacoco.
 */
@QuarkusTest
class EventCoOrganizerServiceSafeGetUserTest {

    private EventCoOrganizerService service;
    private UserServiceClient userClient;

    private final Long eventId = 7777L;
    private final UUID coOrgUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new EventCoOrganizerService();
        userClient = mock(UserServiceClient.class);
        service.userClient = userClient;
    }

    /** A single ACCEPTED co-organizer row pointing at {@link #coOrgUserId}. */
    private EventCoOrganizer acceptedRow() {
        EventCoOrganizer row = new EventCoOrganizer();
        row.id = 1L;
        row.eventId = eventId;
        row.userId = coOrgUserId;
        row.status = CoOrganizerStatus.ACCEPTED;
        return row;
    }

    @Test
    void getCoOrganizers_userHardDeleted_degradesToNullUser() {
        PanacheMock.mock(Event.class, EventCoOrganizer.class);
        when(Event.findByIdOptional(eventId)).thenReturn(Optional.of(new Event()));
        when(EventCoOrganizer.findByEvent(eventId)).thenReturn(List.of(acceptedRow()));
        // Real throw (plain mock, no FT) → exercises catch (NotFoundException).
        when(userClient.getById(coOrgUserId)).thenThrow(new NotFoundException("hard-deleted"));

        List<CoOrganizerDTO> list = service.getCoOrganizers(eventId);

        assertEquals(1, list.size());
        assertNull(list.get(0).displayName(),
                "user-not-found must degrade to a null-enriched DTO");
        assertNull(list.get(0).username());
    }

    @Test
    void getCoOrganizers_userServiceInfraFailure_degradesToNullUser() {
        PanacheMock.mock(Event.class, EventCoOrganizer.class);
        when(Event.findByIdOptional(eventId)).thenReturn(Optional.of(new Event()));
        when(EventCoOrganizer.findByEvent(eventId)).thenReturn(List.of(acceptedRow()));
        // Real throw (plain mock, no FT) → exercises catch (RuntimeException).
        when(userClient.getById(coOrgUserId))
                .thenThrow(new RuntimeException("CB open: user-service unreachable"));

        List<CoOrganizerDTO> list = service.getCoOrganizers(eventId);

        assertEquals(1, list.size());
        assertNull(list.get(0).displayName(),
                "infra failure must degrade to a null-enriched DTO, not propagate");
    }

    @Test
    void getCoOrganizers_nullUserId_skipsLookup() {
        PanacheMock.mock(Event.class, EventCoOrganizer.class);
        when(Event.findByIdOptional(eventId)).thenReturn(Optional.of(new Event()));
        EventCoOrganizer row = acceptedRow();
        row.userId = null; // exercises the userId == null guard in safeGetUser
        when(EventCoOrganizer.findByEvent(eventId)).thenReturn(List.of(row));

        List<CoOrganizerDTO> list = service.getCoOrganizers(eventId);

        assertEquals(1, list.size());
        assertNull(list.get(0).displayName());
        org.mockito.Mockito.verify(userClient, org.mockito.Mockito.never()).getById(any());
    }
}
