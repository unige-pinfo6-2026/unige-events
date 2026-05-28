package ch.unige.events.event.stats.service;

import ch.unige.events.event.coorganizer.entity.EventCoOrganizer;
import ch.unige.events.event.entity.Event;
import ch.unige.events.event.stats.dto.EventStatsDTO;
import ch.unige.events.event.test.JwtTestContext;
import ch.unige.events.event.test.JwtTestHelper;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceSummary;
import ch.unige.events.shared.domain.enums.CoOrganizerStatus;
import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;

import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

@QuarkusTest
@TestSecurity(user = "auth0|stats")
class EventStatsServiceTest {

    @Inject EventStatsService service;
    @Inject EntityManager em;

    @InjectMock @RestClient EngagementServiceClient engagementClient;

    private final UUID creatorId = UUID.randomUUID();

    @BeforeEach
    void stub() {
        JwtTestContext.set(JwtTestHelper.jwtFor(creatorId));
        lenient().when(engagementClient.getAttendanceSummary(anyLong()))
                .thenReturn(AttendanceSummary.of(5L, 2L));
    }

    @AfterEach
    void clear() {
        JwtTestContext.clear();
    }

    private Event create(UUID creator) {
        Event e = new Event();
        e.title = "T";
        e.description = "d";
        e.location = "l";
        e.startDate = LocalDateTime.now().plusDays(1);
        e.endDate = e.startDate.plusHours(2);
        e.category = EventCategory.ACADEMIC;
        e.creatorId = creator;
        e.status = EventStatus.PUBLISHED;
        e.persist();
        return e;
    }

    @Test
    @TestTransaction
    void getStats_byCreator_returnsStats() {
        Event e = create(creatorId);
        em.flush();

        EventStatsDTO stats = service.getStats("auth0|x", e.id);
        assertEquals(5L, stats.attendingCount());
    }

    @Test
    @TestTransaction
    void getStats_unknownEvent_throws404() {
        assertThrows(NotFoundException.class,
                () -> service.getStats("auth0|x", 99999L));
    }

    @Test
    @TestTransaction
    void getStats_byNonCreator_throws403() {
        Event e = create(UUID.randomUUID());
        em.flush();
        assertThrows(ForbiddenException.class,
                () -> service.getStats("auth0|x", e.id));
    }

    @Test
    @TestTransaction
    void getStats_byAcceptedCoOrganizer_succeeds() {
        Event e = create(UUID.randomUUID());
        EventCoOrganizer co = new EventCoOrganizer();
        co.eventId = e.id;
        co.userId = creatorId;
        co.status = CoOrganizerStatus.ACCEPTED;
        co.persist();
        em.flush();

        EventStatsDTO stats = service.getStats("auth0|x", e.id);
        assertEquals(5L, stats.attendingCount());
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "auth0|admin-stats", roles = {"ADMIN"})
    void getStats_byAdminNonCreator_returnsStats() {
        // Admin is neither the creator nor a co-organizer; the ADMIN role
        // alone authorises stats access.
        UUID adminUuid = UUID.randomUUID();
        JwtTestContext.set(JwtTestHelper.jwtFor(adminUuid));

        Event e = create(UUID.randomUUID());
        em.flush();

        EventStatsDTO stats = service.getStats("auth0|admin-stats", e.id);
        assertEquals(5L, stats.attendingCount());
    }

    @Test
    @TestTransaction
    void getStats_anonymousCaller_throws404() {
        Event e = create(creatorId);
        em.flush();
        JwtTestContext.clear();
        assertThrows(NotFoundException.class,
                () -> service.getStats("auth0|x", e.id));
    }

    @Test
    @TestTransaction
    void getStats_summaryNull_returnsZeros() {
        Event e = create(creatorId);
        em.flush();
        lenient().when(engagementClient.getAttendanceSummary(anyLong())).thenReturn(null);

        EventStatsDTO stats = service.getStats("auth0|x", e.id);
        assertEquals(0L, stats.attendingCount());
    }
}
