package ch.unige.events.notification.entity;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sentinel tests for {@link Notification} — verify finder methods,
 * {@code @PrePersist} timestamp init, and bulk update semantics.
 */
@QuarkusTest
class NotificationEntityTest {

    private static Notification persist(UUID userId, NotificationType type, boolean read, LocalDateTime createdAt) {
        Notification n = new Notification();
        n.userId = userId;
        n.type = type;
        n.message = "msg-" + type;
        n.read = read;
        n.createdAt = createdAt;
        n.persist();
        return n;
    }

    @Test
    @TestTransaction
    void prePersist_initializesCreatedAt_whenNull() {
        UUID userId = UUID.randomUUID();
        Notification n = new Notification();
        n.userId = userId;
        n.type = NotificationType.EVENT_CANCELLED;
        n.message = "Hello";
        assertNull(n.createdAt);
        n.persist();
        assertNotNull(n.createdAt);
    }

    @Test
    @TestTransaction
    void prePersist_keepsExistingCreatedAt() {
        LocalDateTime fixed = LocalDateTime.of(2026, 1, 1, 12, 0);
        Notification n = persist(UUID.randomUUID(), NotificationType.EVENT_UPDATED, false, fixed);
        assertEquals(fixed, n.createdAt);
    }

    @Test
    @TestTransaction
    void findByUser_unreadFirst_thenCreatedAtDesc() {
        UUID userId = UUID.randomUUID();
        LocalDateTime t1 = LocalDateTime.of(2026, 5, 17, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 5, 17, 11, 0);
        LocalDateTime t3 = LocalDateTime.of(2026, 5, 17, 12, 0);
        persist(userId, NotificationType.EVENT_CANCELLED, /*read*/ true,  t1); // oldest, read
        persist(userId, NotificationType.EVENT_CANCELLED, /*read*/ true,  t2); // mid, read
        persist(userId, NotificationType.EVENT_UPDATED,   /*read*/ false, t3); // newest, unread

        List<Notification> page = Notification.findByUser(userId, 0, 10);
        assertEquals(3, page.size());
        // Unread row first regardless of createdAt
        assertFalse(page.get(0).read);
        // Read rows in createdAt DESC order (most recent first)
        assertTrue(page.get(1).read);
        assertEquals(t2, page.get(1).createdAt);
        assertEquals(t1, page.get(2).createdAt);
    }

    @Test
    @TestTransaction
    void findByUser_scopedToCaller_noLeak() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        persist(alice, NotificationType.EVENT_UPDATED, false, LocalDateTime.now());
        persist(bob,   NotificationType.EVENT_UPDATED, false, LocalDateTime.now());

        List<Notification> alicePage = Notification.findByUser(alice, 0, 10);
        assertEquals(1, alicePage.size());
        assertEquals(alice, alicePage.get(0).userId);
    }

    @Test
    @TestTransaction
    void findByIdAndUser_mismatchUser_returnsEmpty() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        Notification n = persist(alice, NotificationType.EVENT_UPDATED, false, LocalDateTime.now());

        assertTrue(Notification.findByIdAndUser(n.id, alice).isPresent());
        Optional<Notification> other = Notification.findByIdAndUser(n.id, bob);
        assertTrue(other.isEmpty(), "anti-oracle: same envelope as id inexistant");
    }

    @Test
    @TestTransaction
    void countUnreadByUser_ignoresRead() {
        UUID userId = UUID.randomUUID();
        persist(userId, NotificationType.NEW_ATTENDEE, false, LocalDateTime.now());
        persist(userId, NotificationType.EVENT_UPDATED, false, LocalDateTime.now());
        persist(userId, NotificationType.EVENT_CANCELLED, true, LocalDateTime.now());
        assertEquals(2, Notification.countUnreadByUser(userId));
    }

    @Test
    @TestTransaction
    void markAllReadByUser_bulkUpdates_andSetsReadAt() {
        UUID userId = UUID.randomUUID();
        Notification a = persist(userId, NotificationType.NEW_ATTENDEE,   false, LocalDateTime.now());
        Notification b = persist(userId, NotificationType.EVENT_UPDATED,  false, LocalDateTime.now());
        // Pre-staged read=true row should NOT be touched by the bulk update
        // (filter is read=false). We pin its readAt to a distinctive past
        // value so we can later assert it survived untouched.
        LocalDateTime distinctivePast = LocalDateTime.of(2020, 1, 1, 12, 0);
        Notification c = persist(userId, NotificationType.EVENT_CANCELLED, true, LocalDateTime.now());
        c.readAt = distinctivePast;

        int updated = Notification.markAllReadByUser(userId);
        assertEquals(2, updated);

        // Panache bulk update bypasses the persistence context; the rows
        // already loaded above keep their stale state. flush() pushes our
        // pending writes, then getEntityManager().clear() evicts the
        // cached entities so the next findById reloads from DB.
        Notification.flush();
        Notification.getEntityManager().clear();

        Notification aFresh = Notification.findById(a.id);
        Notification bFresh = Notification.findById(b.id);
        Notification cFresh = Notification.findById(c.id);

        assertTrue(aFresh.read, "ex-unread row should be read after bulk update");
        assertNotNull(aFresh.readAt, "readAt set by bulk update on ex-unread row a");
        assertTrue(bFresh.read);
        assertNotNull(bFresh.readAt);
        // The already-read row was not touched — readAt remains the pinned past.
        assertTrue(cFresh.read);
        assertEquals(distinctivePast, cFresh.readAt,
                "already-read row's readAt must not be touched by the bulk update");
    }

    @Test
    @TestTransaction
    void markAllReadByUser_noUnread_returnsZero() {
        UUID userId = UUID.randomUUID();
        persist(userId, NotificationType.EVENT_CANCELLED, true, LocalDateTime.now());
        int updated = Notification.markAllReadByUser(userId);
        assertEquals(0, updated);
    }
}
