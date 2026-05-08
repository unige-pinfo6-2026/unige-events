package ch.unige.events.service;

import ch.unige.events.config.AppConfig;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.scheduler.ModerationCleanupJob;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ModerationCleanupServiceTest {

    // ── init ───────────────────────────────────────────────────────────────

    @Test
    void init_setsThresholdFromConfig() {
        AppConfig mockConfig = mock(AppConfig.class);
        when(mockConfig.moderationAutoHideThreshold()).thenReturn(5);

        ModerationCleanupService service = new ModerationCleanupService();
        service.appConfig = mockConfig;
        service.init();

        assertEquals(5, service.threshold);
    }

    // ── atOrAboveThreshold ─────────────────────────────────────────────────

    @Test
    void caseA_twoReports_belowThreshold_notHidden() {
        Event event = new Event();
        List<Event> result = ModerationCleanupService.atOrAboveThreshold(Map.of(event, 2L), 3);
        assertTrue(result.isEmpty());
    }

    @Test
    void caseB_threeReports_atThreshold_hidden() {
        Event event = new Event();
        List<Event> result = ModerationCleanupService.atOrAboveThreshold(Map.of(event, 3L), 3);
        assertEquals(1, result.size());
        assertSame(event, result.get(0));
    }

    @Test
    void caseC_fiveReports_aboveThreshold_hidden() {
        Event event = new Event();
        List<Event> result = ModerationCleanupService.atOrAboveThreshold(Map.of(event, 5L), 3);
        assertEquals(1, result.size());
        assertSame(event, result.get(0));
    }

    @Test
    void atOrAboveThreshold_emptyMap_returnsEmpty() {
        assertTrue(ModerationCleanupService.atOrAboveThreshold(Map.of(), 3).isEmpty());
    }

    @Test
    void atOrAboveThreshold_multipleEvents_filtersCorrectly() {
        Event below = new Event();
        Event at = new Event();
        Event above = new Event();
        Map<Event, Long> counts = Map.of(below, 1L, at, 3L, above, 7L);

        List<Event> result = ModerationCleanupService.atOrAboveThreshold(counts, 3);

        assertEquals(2, result.size());
        assertTrue(result.contains(at));
        assertTrue(result.contains(above));
        assertFalse(result.contains(below));
    }

    // ── hide ───────────────────────────────────────────────────────────────

    @Test
    void hide_setsStatusToBanned() {
        Event event = new Event();
        event.status = EventStatus.PUBLISHED;

        ModerationCleanupService service = new ModerationCleanupService();
        service.hide(event);

        assertEquals(EventStatus.BANNED, event.status);
    }

    // ── runCleanup (via spy) ────────────────────────────────────────────────

    @Test
    void runCleanup_caseA_twoReports_doesNotHide() {
        ModerationCleanupService spy = spy(new ModerationCleanupService());
        spy.threshold = 3;
        Event event = new Event();
        event.status = EventStatus.PUBLISHED;

        doReturn(Map.of(event, 2L)).when(spy).fetchPendingReportCounts();
        spy.runCleanup();

        assertEquals(EventStatus.PUBLISHED, event.status);
    }

    @Test
    void runCleanup_caseB_threeReports_hides() {
        ModerationCleanupService spy = spy(new ModerationCleanupService());
        spy.threshold = 3;
        Event event = new Event();
        event.status = EventStatus.PUBLISHED;

        doReturn(Map.of(event, 3L)).when(spy).fetchPendingReportCounts();
        spy.runCleanup();

        assertEquals(EventStatus.BANNED, event.status);
    }

    @Test
    void runCleanup_caseC_fiveReports_hides() {
        ModerationCleanupService spy = spy(new ModerationCleanupService());
        spy.threshold = 3;
        Event event = new Event();
        event.status = EventStatus.PUBLISHED;

        doReturn(Map.of(event, 5L)).when(spy).fetchPendingReportCounts();
        spy.runCleanup();

        assertEquals(EventStatus.BANNED, event.status);
    }

    @Test
    void runCleanup_noEvents_callsFetchPendingReportCounts() {
        ModerationCleanupService spy = spy(new ModerationCleanupService());
        spy.threshold = 3;

        doReturn(Map.of()).when(spy).fetchPendingReportCounts();
        spy.runCleanup();

        verify(spy).fetchPendingReportCounts();
    }

    // ── fetchPendingReportCounts (mocked EntityManager) ────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void fetchPendingReportCounts_mapsQueryResultsToEventCounts() {
        Event event1 = new Event();
        Event event2 = new Event();

        TypedQuery<Object[]> mockQuery = mock(TypedQuery.class);
        EntityManager mockEm = mock(EntityManager.class);
        when(mockEm.createQuery(anyString(), eq(Object[].class))).thenReturn(mockQuery);
        when(mockQuery.setParameter(anyString(), any())).thenReturn(mockQuery);
        when(mockQuery.getResultList()).thenReturn(
                List.of(new Object[]{event1, 4L}, new Object[]{event2, 2L})
        );

        ModerationCleanupService service = new ModerationCleanupService();
        service.em = mockEm;

        Map<Event, Long> result = service.fetchPendingReportCounts();

        assertEquals(2, result.size());
        assertEquals(4L, result.get(event1));
        assertEquals(2L, result.get(event2));
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchPendingReportCounts_emptyDb_returnsEmptyMap() {
        TypedQuery<Object[]> mockQuery = mock(TypedQuery.class);
        EntityManager mockEm = mock(EntityManager.class);
        when(mockEm.createQuery(anyString(), eq(Object[].class))).thenReturn(mockQuery);
        when(mockQuery.setParameter(anyString(), any())).thenReturn(mockQuery);
        when(mockQuery.getResultList()).thenReturn(List.of());

        ModerationCleanupService service = new ModerationCleanupService();
        service.em = mockEm;

        assertTrue(service.fetchPendingReportCounts().isEmpty());
    }

    // ── ModerationCleanupJob ────────────────────────────────────────────────

    @Test
    void job_run_delegatesToService() {
        ModerationCleanupService mockService = mock(ModerationCleanupService.class);
        ModerationCleanupJob job = new ModerationCleanupJob(mockService);

        job.run();

        verify(mockService).runCleanup();
    }
}
