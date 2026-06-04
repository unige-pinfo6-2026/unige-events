package ch.unige.events.event.service;

import ch.unige.events.event.dto.EventDTO;
import ch.unige.events.event.entity.Event;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceSummary;
import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.enums.Faculty;

import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@QuarkusTest
@SuppressWarnings("java:S100")
class EventSearchServiceTest {

    @Inject EventSearchService service;
    @Inject EntityManager em;

    @InjectMock @RestClient EngagementServiceClient engagementClient;

    @BeforeEach
    void stub() {
        lenient().when(engagementClient.getAttendanceSummariesBulk(any())).thenReturn(Map.of());
    }

    private Event publish(String title, EventCategory cat, Faculty fac, List<String> tags) {
        Event e = new Event();
        e.title = title;
        e.description = title + " desc";
        e.location = "loc";
        e.startDate = LocalDateTime.of(2999, 1, 1, 0, 0).plusDays(1);
        e.endDate = e.startDate.plusHours(2);
        e.category = cat;
        e.faculty = fac;
        e.creatorId = UUID.randomUUID();
        e.status = EventStatus.PUBLISHED;
        if (tags != null) e.tags = new java.util.ArrayList<>(tags);
        e.persist();
        return e;
    }

    @Test
    @TestTransaction
    void search_noFilters_returnsPublishedOnly() {
        Event pub = publish("Pub1", EventCategory.ACADEMIC, null, null);
        Event draft = new Event();
        draft.title = "draft";
        draft.description = "d";
        draft.location = "l";
        draft.startDate = LocalDateTime.of(2999, 1, 1, 0, 0).plusDays(1);
        draft.endDate = draft.startDate.plusHours(2);
        draft.category = EventCategory.ACADEMIC;
        draft.creatorId = UUID.randomUUID();
        draft.status = EventStatus.DRAFT;
        draft.persist();
        em.flush();

        List<EventDTO> r = service.search(null, null, null, null, null, null, null, 0, 100);
        assertTrue(r.stream().anyMatch(d -> d.id().equals(pub.id)));
        assertTrue(r.stream().noneMatch(d -> d.id().equals(draft.id)));
    }

    @Test
    @TestTransaction
    void search_byQText_matchesTitle() {
        publish("Yoga session", EventCategory.SPORTS, null, null);
        publish("Other", EventCategory.SPORTS, null, null);
        em.flush();

        List<EventDTO> r = service.search("yoga", null, null, null, null, null, null, 0, 20);
        assertFalse(r.isEmpty());
    }

    @Test
    @TestTransaction
    void search_byCategory() {
        publish("a", EventCategory.SOCIAL, null, null);
        publish("b", EventCategory.SPORTS, null, null);
        em.flush();

        List<EventDTO> r = service.search(null, EventCategory.SPORTS, null, null, null, null, null, 0, 20);
        assertFalse(r.isEmpty());
    }

    @Test
    @TestTransaction
    void search_byFaculty() {
        publish("a", EventCategory.SPORTS, Faculty.SCIENCES, null);
        publish("b", EventCategory.SPORTS, Faculty.LETTERS, null);
        em.flush();

        List<EventDTO> r = service.search(null, null, Faculty.SCIENCES, null, null, null, null, 0, 20);
        assertFalse(r.isEmpty());
    }

    @Test
    @TestTransaction
    void search_byFacultyNone() {
        publish("a", EventCategory.SPORTS, null, null);
        publish("b", EventCategory.SPORTS, Faculty.LETTERS, null);
        em.flush();

        List<EventDTO> r = service.search(null, null, null, true, null, null, null, 0, 20);
        assertFalse(r.isEmpty());
    }

    @Test
    @TestTransaction
    void search_byTags() {
        publish("a", EventCategory.SPORTS, null, List.of("foo"));
        publish("b", EventCategory.SPORTS, null, List.of("bar"));
        em.flush();

        List<EventDTO> r = service.search(null, null, null, null, List.of("foo"), null, null, 0, 20);
        assertFalse(r.isEmpty());
    }

    @Test
    @TestTransaction
    void search_byDateRange() {
        publish("a", EventCategory.SPORTS, null, null);
        em.flush();

        // publish() puts the event at the FUTURE anchor (2999-01-02); the
        // search window must sit on the same anchor to contain it (the bound was
        // originally now()-relative, like the event's now().plusDays(1) startDate).
        LocalDate today = LocalDate.of(2999, 1, 1);
        List<EventDTO> r = service.search(null, null, null, null, null,
                today, today.plusDays(30), 0, 20);
        assertFalse(r.isEmpty());
    }

    @Test
    @TestTransaction
    void search_emptyResult() {
        List<EventDTO> r = service.search("xxxx", null, null, null, null, null, null, 0, 20);
        assertTrue(r.isEmpty());
    }

    @Test
    @TestTransaction
    void search_blankQuery_treatedAsAbsent() {
        publish("a", EventCategory.SPORTS, null, null);
        em.flush();
        List<EventDTO> r = service.search("   ", null, null, null, null, null, null, 0, 20);
        assertFalse(r.isEmpty());
    }

    @Test
    @TestTransaction
    void search_dateFromOnly() {
        publish("a", EventCategory.SPORTS, null, null);
        em.flush();
        List<EventDTO> r = service.search(null, null, null, null, null,
                LocalDate.of(2000, 1, 1).minusDays(1), null, 0, 20);
        assertFalse(r.isEmpty());
    }

    @Test
    @TestTransaction
    void search_dateToOnly() {
        publish("a", EventCategory.SPORTS, null, null);
        em.flush();
        List<EventDTO> r = service.search(null, null, null, null, null,
                null, LocalDate.of(2999, 1, 1).plusDays(30), 0, 20);
        assertFalse(r.isEmpty());
    }

    @Test
    @TestTransaction
    void search_summariesNullFromClient_safeDefault() {
        publish("a", EventCategory.SPORTS, null, null);
        em.flush();

        when(engagementClient.getAttendanceSummariesBulk(any())).thenReturn(null);
        List<EventDTO> r = service.search(null, null, null, null, null, null, null, 0, 20);
        assertFalse(r.isEmpty());
    }
}
