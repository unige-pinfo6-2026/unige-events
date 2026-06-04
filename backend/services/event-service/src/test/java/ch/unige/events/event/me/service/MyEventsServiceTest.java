package ch.unige.events.event.me.service;

import ch.unige.events.event.entity.Event;
import ch.unige.events.event.me.dto.EventDTO;
import ch.unige.events.event.test.JwtTestContext;
import ch.unige.events.event.test.JwtTestHelper;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;

import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestSecurity(user = "auth0|me")
class MyEventsServiceTest {

    @Inject MyEventsService service;
    @Inject EntityManager em;

    @InjectMock @RestClient EngagementServiceClient engagementClient;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        JwtTestContext.set(JwtTestHelper.jwtFor(userId));
        lenient().when(engagementClient.getAttendanceSummariesBulk(any())).thenReturn(Map.of());
    }

    @AfterEach
    void clear() {
        JwtTestContext.clear();
    }

    private Event create(UUID creator, EventStatus status) {
        Event e = new Event();
        e.title = "T";
        e.description = "d";
        e.location = "l";
        e.startDate = LocalDateTime.of(2999, Month.JANUARY, 1, 0, 0).plusDays(1);
        e.endDate = e.startDate.plusHours(2);
        e.category = EventCategory.ACADEMIC;
        e.creatorId = creator;
        e.status = status;
        e.persist();
        return e;
    }

    @Test
    @TestTransaction
    void getMyEvents_returnsCallerEvents() {
        create(userId, EventStatus.PUBLISHED);
        create(UUID.randomUUID(), EventStatus.PUBLISHED);
        em.flush();
        List<EventDTO> list = service.getMyEvents("auth0|x", null, 0, 20);
        assertEquals(1, list.size());
    }

    @Test
    @TestTransaction
    void getMyEvents_filterByStatus() {
        create(userId, EventStatus.PUBLISHED);
        create(userId, EventStatus.DRAFT);
        em.flush();
        List<EventDTO> list = service.getMyEvents("auth0|x", EventStatus.DRAFT, 0, 20);
        assertEquals(1, list.size());
    }

    @Test
    @TestTransaction
    void getMyEvents_anonymous_throws404() {
        JwtTestContext.clear();
        assertThrows(NotFoundException.class,
                () -> service.getMyEvents("auth0|x", null, 0, 20));
    }

    @Test
    @TestTransaction
    void getMyEvents_emptyForNewUser() {
        assertTrue(service.getMyEvents("auth0|x", null, 0, 20).isEmpty());
    }

    @Test
    @TestTransaction
    void getMyEvents_nullTagsEvent_yieldsEmptyTagList() {
        // me/dto/EventDTO L105 tags==null arm — Event.tags defaults to an empty
        // ArrayList, so it must be explicitly nulled. Driven through the
        // @QuarkusTest service so jacoco counts the executed DTO bytecode.
        Event e = create(userId, EventStatus.PUBLISHED);
        e.tags = null;
        em.flush();

        List<EventDTO> list = service.getMyEvents("auth0|x", null, 0, 20);
        assertEquals(1, list.size());
        assertEquals(List.of(), list.get(0).tags());
    }

    @Test
    @TestTransaction
    void getMyEvents_summariesNullClient_safe() {
        // P2: the bulk-summary client may return null (its @Fallback default).
        // toEventDTOs must coalesce to an empty map and enrich with zeroed
        // counts rather than NPE.
        create(userId, EventStatus.PUBLISHED);
        em.flush();
        when(engagementClient.getAttendanceSummariesBulk(any())).thenReturn(null);

        List<EventDTO> list = service.getMyEvents("auth0|x", null, 0, 20);
        assertEquals(1, list.size());
    }
}
