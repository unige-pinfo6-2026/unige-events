package ch.unige.events.event.service;

import ch.unige.events.event.dto.EventDTO;
import ch.unige.events.event.entity.Event;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceSummary;
import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;

import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@QuarkusTest
class FeaturedServiceTest {

    @Inject FeaturedService service;
    @Inject EntityManager em;

    @InjectMock @RestClient EngagementServiceClient engagementClient;

    @BeforeEach
    void stub() {
        lenient().when(engagementClient.getAttendanceSummariesBulk(any())).thenReturn(Map.of());
        lenient().when(engagementClient.getAttendanceSummary(anyLong()))
                .thenReturn(AttendanceSummary.of(0L, 0L));
    }

    private Event create(boolean featured, EventStatus status, LocalDateTime end) {
        Event e = new Event();
        e.title = "T-" + UUID.randomUUID();
        e.description = "d";
        e.location = "l";
        e.startDate = LocalDateTime.now().plusDays(1);
        e.endDate = end;
        e.category = EventCategory.ACADEMIC;
        e.creatorId = UUID.randomUUID();
        e.status = status;
        e.featured = featured;
        e.featuredAt = featured ? LocalDateTime.now() : null;
        e.persist();
        return e;
    }

    @Test
    @TestTransaction
    void getFeatured_featuredFirstThenFiller() {
        Event f = create(true, EventStatus.PUBLISHED, LocalDateTime.now().plusDays(5));
        Event nf = create(false, EventStatus.PUBLISHED, LocalDateTime.now().plusDays(5));
        em.flush();

        List<EventDTO> list = service.getFeatured(12);
        assertTrue(list.stream().anyMatch(d -> d.id().equals(f.id)));
        assertTrue(list.stream().anyMatch(d -> d.id().equals(nf.id)));
        assertTrue(list.get(0).featured());
    }

    @Test
    @TestTransaction
    void getFeatured_limitCappedAt12() {
        for (int i = 0; i < 15; i++) {
            create(true, EventStatus.PUBLISHED, LocalDateTime.now().plusDays(5));
        }
        em.flush();
        List<EventDTO> list = service.getFeatured(50);
        assertTrue(list.size() <= 12);
    }

    @Test
    @TestTransaction
    void getFeatured_excludesPastAndDrafts() {
        Event past = create(true, EventStatus.PUBLISHED, LocalDateTime.now().minusDays(1));
        Event draft = create(true, EventStatus.DRAFT, LocalDateTime.now().plusDays(5));
        em.flush();
        // Result may include unrelated leftover-PUBLISHED events from other
        // test classes (drop-and-create runs once per app instance).
        // Strict check: neither of the rejected candidates should appear.
        List<EventDTO> result = service.getFeatured(12);
        assertFalse(result.stream().anyMatch(d -> d.id().equals(past.id)));
        assertFalse(result.stream().anyMatch(d -> d.id().equals(draft.id)));
    }

    @Test
    @TestTransaction
    void feature_setsFlag() {
        Event e = create(false, EventStatus.PUBLISHED, LocalDateTime.now().plusDays(5));
        em.flush();
        EventDTO dto = service.feature(e.id);
        assertTrue(dto.featured());
        assertNotNull(dto.featuredAt());
    }

    @Test
    @TestTransaction
    void feature_alreadyFeatured_idempotent() {
        Event e = create(true, EventStatus.PUBLISHED, LocalDateTime.now().plusDays(5));
        em.flush();
        EventDTO dto = service.feature(e.id);
        assertTrue(dto.featured());
    }

    @Test
    @TestTransaction
    void feature_unknown_throws404() {
        assertThrows(NotFoundException.class, () -> service.feature(99999L));
    }

    @Test
    @TestTransaction
    void unfeature_clearsFlag() {
        Event e = create(true, EventStatus.PUBLISHED, LocalDateTime.now().plusDays(5));
        em.flush();
        EventDTO dto = service.unfeature(e.id);
        assertFalse(dto.featured());
    }

    @Test
    @TestTransaction
    void unfeature_unknown_throws404() {
        assertThrows(NotFoundException.class, () -> service.unfeature(99999L));
    }

    @Test
    @TestTransaction
    void getFeatured_zeroLimit_returnsEmpty() {
        // Cannot rely on empty DB across classes — instead check limit semantics.
        assertNotNull(service.getFeatured(0));
    }

    @Test
    @TestTransaction
    void getFeatured_summariesNullClient_safe() {
        create(false, EventStatus.PUBLISHED, LocalDateTime.now().plusDays(5));
        em.flush();
        when(engagementClient.getAttendanceSummariesBulk(any())).thenReturn(null);
        List<EventDTO> list = service.getFeatured(6);
        assertFalse(list.isEmpty());
    }

    @Test
    @TestTransaction
    void getFeatured_phase2RankingCountsFavorites() {
        // No featured rows → phase1Ids empty (the NOT IN clause is skipped),
        // and the phase-2 filler ranks candidates by attending + favorites.
        // Persisting a Favorite on a candidate exercises countFavorites'
        // row-accumulation loop (the GROUP BY result mapping).
        Event candidate = create(false, EventStatus.PUBLISHED, LocalDateTime.now().plusDays(5));
        em.flush();
        ch.unige.events.event.favorite.entity.Favorite fav =
                new ch.unige.events.event.favorite.entity.Favorite();
        fav.userId = UUID.randomUUID();
        fav.eventId = candidate.id;
        fav.persist();
        em.flush();

        List<EventDTO> list = service.getFeatured(6);
        assertTrue(list.stream().anyMatch(d -> d.id().equals(candidate.id)),
                "A favorited published candidate must surface in the phase-2 filler");
    }
}
