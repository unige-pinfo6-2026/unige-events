package ch.unige.events.notification.service;

import ch.unige.events.notification.dto.NotificationDTO;
import ch.unige.events.notification.entity.Notification;
import ch.unige.events.notification.entity.NotificationType;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.IdProjection;

import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import java.time.Month;

/**
 * Unit-flavor tests for {@link NotificationService} — wire the
 * {@link UserServiceClient} as a Mockito mock so identity resolution is
 * deterministic, then exercise the business logic on the real Panache
 * datasource (DevServices Postgres).
 */
@QuarkusTest
class NotificationServiceTest {

    @Inject NotificationService service;
    @InjectMock @RestClient UserServiceClient userClient;

    private static final String AUTH0 = "auth0|abc";
    private static final UUID CALLER = UUID.randomUUID();

    private void mockResolveToCaller() {
        when(userClient.getInternalByAuth0Id(anyString()))
                .thenReturn(new IdProjection(CALLER));
    }

    @Test
    @TestTransaction
    void create_persistsRowWithDefaults() {
        Notification n = service.create(CALLER, NotificationType.EVENT_CANCELLED, 42L, null,
                "L'événement a été annulé.");
        assertNotNull(n.id);
        assertEquals(CALLER, n.userId);
        assertEquals(NotificationType.EVENT_CANCELLED, n.type);
        assertEquals(42L, n.eventId);
        assertNull(n.relatedUserId);
        assertEquals("L'événement a été annulé.", n.message);
        assertFalse(n.read);
        assertNull(n.readAt);
        assertNotNull(n.createdAt);
    }

    @Test
    @TestTransaction
    void create_atLeastOnce_allowsDuplicateRows() {
        // Décision D: at-least-once is accepted; two identical creates produce
        // two rows. No UK-based deduplication in phase 1.
        service.create(CALLER, NotificationType.EVENT_UPDATED, 1L, null, "msg");
        service.create(CALLER, NotificationType.EVENT_UPDATED, 1L, null, "msg");
        assertEquals(2, Notification.count("userId = ?1 and eventId = ?2", CALLER, 1L));
    }

    @Test
    @TestTransaction
    void listMine_resolvesAuth0IdAndReturnsOnlyCallerRows() {
        mockResolveToCaller();
        UUID other = UUID.randomUUID();
        service.create(CALLER, NotificationType.EVENT_UPDATED, 1L, null, "for caller");
        service.create(other, NotificationType.EVENT_UPDATED, 1L, null, "for other");

        List<NotificationDTO> page = service.listMine(AUTH0, 0, 20);
        assertEquals(1, page.size());
        assertEquals(CALLER, page.get(0).userId());
    }

    @Test
    @TestTransaction
    void countUnread_excludesReadRows() {
        mockResolveToCaller();
        Notification a = service.create(CALLER, NotificationType.EVENT_UPDATED, 1L, null, "a");
        a.read = true;
        service.create(CALLER, NotificationType.NEW_ATTENDEE, 2L, null, "b");
        service.create(CALLER, NotificationType.EVENT_CANCELLED, 3L, null, "c");

        assertEquals(2L, service.countUnread(AUTH0));
    }

    @Test
    @TestTransaction
    void markRead_setsReadTrueAndReadAt() {
        mockResolveToCaller();
        Notification n = service.create(CALLER, NotificationType.NEW_ATTENDEE, 1L, null, "x");

        service.markRead(AUTH0, n.id);

        Notification fresh = Notification.findById(n.id);
        assertTrue(fresh.read);
        assertNotNull(fresh.readAt);
    }

    @Test
    @TestTransaction
    void markRead_idempotent_doesNotOverwriteReadAt() {
        mockResolveToCaller();
        // Pre-stage a row already read with a distinctive past readAt — so we
        // can assert the second markRead leaves it untouched without relying
        // on Thread.sleep (Sonar S2925) to differentiate two now() calls.
        Notification n = service.create(CALLER, NotificationType.NEW_ATTENDEE, 1L, null, "x");
        java.time.LocalDateTime distinctivePast = java.time.LocalDateTime.of(2020, Month.JANUARY, 1, 12, 0);
        n.read = true;
        n.readAt = distinctivePast;

        service.markRead(AUTH0, n.id);

        java.time.LocalDateTime after = Notification.<Notification>findById(n.id).readAt;
        assertEquals(distinctivePast, after,
                "idempotent re-mark on an already-read row must not overwrite readAt");
    }

    @Test
    @TestTransaction
    void markRead_otherUsersRow_throwsNotFound_antiOracle() {
        mockResolveToCaller();
        UUID other = UUID.randomUUID();
        Notification foreign = service.create(other, NotificationType.EVENT_UPDATED, 1L, null, "not yours");

        assertThrows(NotFoundException.class, () -> service.markRead(AUTH0, foreign.id));
    }

    @Test
    @TestTransaction
    void markRead_unknownId_throwsNotFound() {
        mockResolveToCaller();
        assertThrows(NotFoundException.class, () -> service.markRead(AUTH0, 99_999_999L));
    }

    @Test
    @TestTransaction
    void markAllRead_bulkSetsAndReturnsCount() {
        mockResolveToCaller();
        service.create(CALLER, NotificationType.EVENT_UPDATED, 1L, null, "a");
        service.create(CALLER, NotificationType.EVENT_UPDATED, 2L, null, "b");
        service.create(CALLER, NotificationType.EVENT_UPDATED, 3L, null, "c");

        long updated = service.markAllRead(AUTH0);
        assertEquals(3L, updated);

        // Panache bulk update bypasses the persistence context — flush our
        // pending writes then clear() to evict cached stale entities before
        // re-reading from DB.
        Notification.flush();
        Notification.getEntityManager().clear();
        List<Notification> rows = Notification.<Notification>list("userId", CALLER);
        for (Notification n : rows) {
            assertTrue(n.read);
            assertNotNull(n.readAt);
        }
    }

    @Test
    @TestTransaction
    void markAllRead_noUnread_returnsZero() {
        mockResolveToCaller();
        Notification n = service.create(CALLER, NotificationType.EVENT_UPDATED, 1L, null, "a");
        n.read = true;

        assertEquals(0L, service.markAllRead(AUTH0));
    }

    @Test
    @TestTransaction
    void resolveUserId_nullProjection_throwsNotFound() {
        // Defensive branch: if a misbehaving REST client impl returns a
        // non-null IdProjection with null id (the @Fallback throws 503 on
        // downstream outage so we should not reach here, but a degraded
        // server-side mock could).
        when(userClient.getInternalByAuth0Id(anyString()))
                .thenReturn(new IdProjection(null));

        assertThrows(NotFoundException.class, () -> service.listMine(AUTH0, 0, 20));
    }

    @Test
    @TestTransaction
    void resolveUserId_nullReturn_throwsNotFound() {
        when(userClient.getInternalByAuth0Id(anyString())).thenReturn(null);
        assertThrows(NotFoundException.class, () -> service.countUnread(AUTH0));
    }

    @Test
    @TestTransaction
    void listMine_emptyInbox_returnsEmptyList() {
        mockResolveToCaller();
        List<NotificationDTO> page = service.listMine(AUTH0, 0, 20);
        assertEquals(0, page.size());
    }

    @Test
    @TestTransaction
    void listMine_paginationWorks() {
        mockResolveToCaller();
        for (int i = 0; i < 15; i++) {
            service.create(CALLER, NotificationType.EVENT_UPDATED, (long) i, null, "msg" + i);
        }
        List<NotificationDTO> page0 = service.listMine(AUTH0, 0, 10);
        List<NotificationDTO> page1 = service.listMine(AUTH0, 1, 10);
        assertEquals(10, page0.size());
        assertEquals(5, page1.size());
    }

    @Test
    @TestTransaction
    void countUnread_zeroWhenAllRead_orEmpty() {
        mockResolveToCaller();
        assertEquals(0L, service.countUnread(AUTH0));
    }

    @Test
    @TestTransaction
    void create_acceptsNullRelatedUserId_andNullEventId() {
        Notification n = service.create(CALLER, NotificationType.EVENT_REMINDER, null, null, "future reminder");
        assertNotNull(n.id);
        assertEquals(NotificationType.EVENT_REMINDER, n.type);
        assertNotNull(n.createdAt);
        assertEquals("future reminder", n.message);
    }
}
