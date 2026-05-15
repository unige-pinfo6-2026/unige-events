package ch.unige.events.report.service;

import ch.unige.events.report.config.AppConfig;
import ch.unige.events.report.entity.Report;
import ch.unige.events.shared.domain.enums.ReportReason;
import ch.unige.events.shared.domain.enums.ReportStatus;
import ch.unige.events.shared.kafka.events.EventBannedEvent;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class ModerationCleanupServiceTest {

    @Inject
    EntityManager realEm;

    @SuppressWarnings("unchecked")
    private static ModerationCleanupService buildService(int threshold,
                                                         Event<EventBannedEvent> bannedEvent,
                                                         EntityManager em,
                                                         AppConfig config) throws Exception {
        ModerationCleanupService svc = new ModerationCleanupService();
        setField(svc, "appConfig", config);
        setField(svc, "em", em);
        setField(svc, "bannedEvent", bannedEvent);
        svc.threshold = threshold;
        return svc;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = ModerationCleanupService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void atOrAboveThreshold_filtersByValue() {
        Map<Long, Long> counts = new HashMap<>();
        counts.put(1L, 1L);
        counts.put(2L, 2L);
        counts.put(3L, 5L);
        List<Long> over = ModerationCleanupService.atOrAboveThreshold(counts, 2);
        assertEquals(2, over.size());
        assertTrue(over.contains(2L));
        assertTrue(over.contains(3L));
        assertTrue(!over.contains(1L));
    }

    @Test
    void atOrAboveThreshold_emptyMap_returnsEmpty() {
        List<Long> over = ModerationCleanupService.atOrAboveThreshold(Map.of(), 5);
        assertTrue(over.isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test
    void hide_firesEventBannedEvent() throws Exception {
        Event<EventBannedEvent> spy = mock(Event.class);
        ModerationCleanupService svc = buildService(2, spy, realEm, mock(AppConfig.class));

        svc.hide(77L);

        ArgumentCaptor<EventBannedEvent> cap = ArgumentCaptor.forClass(EventBannedEvent.class);
        verify(spy).fire(cap.capture());
        EventBannedEvent ev = cap.getValue();
        assertEquals(77L, ev.eventId());
        assertEquals("auto-cleanup-threshold-exceeded", ev.reason());
        assertNull(ev.bannedBy());
    }

    @SuppressWarnings("unchecked")
    @Test
    @TestTransaction
    void runCleanup_withCountsAtOrAboveThreshold_firesBannedEventForEachEvent() throws Exception {
        Long aboveEventId = 900_001L;
        Long belowEventId = 900_002L;
        persistPending(aboveEventId, UUID.randomUUID());
        persistPending(aboveEventId, UUID.randomUUID());
        persistPending(belowEventId, UUID.randomUUID());

        Event<EventBannedEvent> spy = mock(Event.class);
        ModerationCleanupService svc = buildService(2, spy, realEm, mock(AppConfig.class));
        svc.runCleanup();

        ArgumentCaptor<EventBannedEvent> cap = ArgumentCaptor.forClass(EventBannedEvent.class);
        verify(spy, atLeastOnce()).fire(cap.capture());
        boolean firedForAbove = cap.getAllValues().stream().anyMatch(e -> e.eventId() == aboveEventId);
        boolean firedForBelow = cap.getAllValues().stream().anyMatch(e -> e.eventId() == belowEventId);
        assertTrue(firedForAbove);
        assertTrue(!firedForBelow);
    }

    @SuppressWarnings("unchecked")
    @Test
    @TestTransaction
    void runCleanup_noPendingRows_doesNotFire() throws Exception {
        // Use a fresh EntityManager via a mock that returns an empty result list.
        EntityManager emMock = mock(EntityManager.class);
        @SuppressWarnings("unchecked")
        jakarta.persistence.TypedQuery<Object[]> q = mock(jakarta.persistence.TypedQuery.class);
        when(emMock.createQuery(anyString(), any(Class.class))).thenReturn(q);
        when(q.setParameter(anyString(), any())).thenReturn(q);
        when(q.getResultList()).thenReturn(List.of());

        Event<EventBannedEvent> spy = mock(Event.class);
        ModerationCleanupService svc = buildService(2, spy, emMock, mock(AppConfig.class));
        svc.runCleanup();
        verify(spy, never()).fire(any(EventBannedEvent.class));
    }

    @Test
    @TestTransaction
    void fetchPendingReportCounts_groupsByEventId() throws Exception {
        Long eventA = 901_001L;
        Long eventB = 901_002L;
        persistPending(eventA, UUID.randomUUID());
        persistPending(eventA, UUID.randomUUID());
        persistPending(eventB, UUID.randomUUID());

        @SuppressWarnings("unchecked")
        Event<EventBannedEvent> spy = mock(Event.class);
        ModerationCleanupService svc = buildService(2, spy, realEm, mock(AppConfig.class));

        Map<Long, Long> result = svc.fetchPendingReportCounts();
        assertEquals(Long.valueOf(2L), result.get(eventA));
        assertEquals(Long.valueOf(1L), result.get(eventB));
    }

    @SuppressWarnings("unchecked")
    @Test
    void postConstruct_init_pullsThresholdFromConfig() throws Exception {
        AppConfig config = mock(AppConfig.class);
        when(config.moderationAutoHideThreshold()).thenReturn(7);
        ModerationCleanupService svc = buildService(0, mock(Event.class), realEm, config);
        svc.init();
        assertEquals(7, svc.threshold);
    }

    @SuppressWarnings("unchecked")
    @Test
    void runCleanup_thresholdZero_logsAndCompletes() throws Exception {
        EntityManager emMock = mock(EntityManager.class);
        @SuppressWarnings("unchecked")
        jakarta.persistence.TypedQuery<Object[]> q = mock(jakarta.persistence.TypedQuery.class);
        when(emMock.createQuery(anyString(), any(Class.class))).thenReturn(q);
        when(q.setParameter(anyString(), any())).thenReturn(q);
        when(q.getResultList()).thenReturn(List.of());

        Event<EventBannedEvent> spy = mock(Event.class);
        ModerationCleanupService svc = buildService(0, spy, emMock, mock(AppConfig.class));
        svc.runCleanup();
        // No reports to count → nothing fires.
        verify(spy, never()).fire(any(EventBannedEvent.class));
    }

    private static void persistPending(Long eventId, UUID reporterId) {
        Report r = new Report();
        r.eventId = eventId;
        r.reporterId = reporterId;
        r.reason = ReportReason.SPAM;
        r.status = ReportStatus.PENDING;
        r.persist();
    }

    static {
        // Make sure the assertion of method signatures still holds.
        assertNotNull(EventBannedEvent.class);
    }
}
