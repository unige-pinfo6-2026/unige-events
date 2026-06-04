package ch.unige.events.event.attachment.entity;

import ch.unige.events.event.entity.Event;
import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SCRUM-148 — schema + helper sanity for {@link EventAttachment} (Décision P).
 *
 * <p>{@code @QuarkusTest} so Jacoco picks up the production class (piège #1)
 * — also because the schema-level CHECK constraints can only be exercised
 * via a real Postgres (DevServices in CI). The {@code @TestTransaction}
 * pattern rolls back after each method so the rows do not leak between
 * tests.
 */
@QuarkusTest
class EventAttachmentEntityTest {

    @Inject EntityManager em;

    /**
     * Persists a minimal parent {@link Event} so the FK
     * {@code event_attachments.event_id} resolves. The CASCADE FK and the
     * row purge on event delete are exercised by
     * {@code EventServiceDeleteCascadeAttachmentsTest} (étape 19) — here we
     * just need a stable id to attach to.
     */
    private Event persistedEvent() {
        Event e = new Event();
        e.title = "Test-Event";
        e.description = "for attachment test";
        e.location = "Geneva";
        e.startDate = LocalDateTime.of(2999, 1, 1, 0, 0).plusDays(1);
        e.endDate = LocalDateTime.of(2999, 1, 1, 0, 0).plusDays(2);
        e.category = EventCategory.CONFERENCE;
        e.creatorId = UUID.randomUUID();
        e.status = EventStatus.PUBLISHED;
        e.persist();
        em.flush();
        return e;
    }

    private EventAttachment newAttachment(Long eventId, String name, long size, String mime) {
        EventAttachment a = new EventAttachment();
        a.eventId = eventId;
        a.fileName = name;
        a.fileUrl = "event-attachments/" + UUID.randomUUID();
        a.fileSize = size;
        a.mimeType = mime;
        a.uploadedById = UUID.randomUUID();
        return a;
    }

    @Test
    @TestTransaction
    void prePersist_setsUploadedAtWhenNull() {
        Event ev = persistedEvent();
        EventAttachment a = newAttachment(ev.id, "deck.pdf", 1024L, "application/pdf");
        a.persist();
        em.flush();
        assertNotNull(a.uploadedAt, "@PrePersist must populate uploadedAt");
    }

    @Test
    @TestTransaction
    void findByEvent_sortsByUploadedAtAscending() {
        Event ev = persistedEvent();
        // First insert — explicit timestamp guarantees a deterministic order
        // regardless of @PrePersist millisecond resolution.
        EventAttachment first = newAttachment(ev.id, "early.pdf", 1024L, "application/pdf");
        first.uploadedAt = LocalDateTime.of(2000, 1, 1, 0, 0).minusMinutes(10);
        first.persist();
        EventAttachment second = newAttachment(ev.id, "late.pdf", 2048L, "application/pdf");
        second.uploadedAt = LocalDateTime.of(2025, 1, 1, 12, 0);
        second.persist();
        em.flush();

        List<EventAttachment> ordered = EventAttachment.findByEvent(ev.id);
        assertEquals(2, ordered.size());
        assertEquals("early.pdf", ordered.get(0).fileName);
        assertEquals("late.pdf", ordered.get(1).fileName);
    }

    @Test
    @TestTransaction
    void findByEvent_returnsEmptyForUnknownEvent() {
        // No insert — query must return an empty list, not throw.
        List<EventAttachment> none = EventAttachment.findByEvent(9_999_999L);
        assertTrue(none.isEmpty());
    }

    @Test
    @TestTransaction
    void countByEvent_returnsRowCount() {
        Event ev = persistedEvent();
        newAttachment(ev.id, "a.pdf", 1L, "application/pdf").persist();
        newAttachment(ev.id, "b.pdf", 2L, "application/pdf").persist();
        newAttachment(ev.id, "c.pdf", 3L, "application/pdf").persist();
        em.flush();
        assertEquals(3L, EventAttachment.countByEvent(ev.id));
    }

    /**
     * Sentinel for the DB CHECK constraint {@code event_attachments_size_check}.
     * The service layer rejects oversized uploads with 422 first (Décision V)
     * — this test exists so a regression that skips the service layer (e.g.
     * a future internal endpoint) still triggers a constraint violation at
     * the DB level.
     */
    @Test
    @TestTransaction
    void dbCheckSize_rejectsOversized() {
        Event ev = persistedEvent();
        EventAttachment oversize = newAttachment(ev.id, "huge.pdf",
                10L * 1024L * 1024L + 1L, "application/pdf");
        oversize.persist();
        assertThrows(PersistenceException.class, em::flush,
                "DB CHECK event_attachments_size_check must reject > 10 MiB");
    }

    /**
     * Sentinel for the DB CHECK constraint {@code event_attachments_mime_check}.
     * Same rationale as {@link #dbCheckSize_rejectsOversized()} — last line
     * of defense against any path that bypasses the service-level whitelist.
     */
    @Test
    @TestTransaction
    void dbCheckMime_rejectsInvalid() {
        Event ev = persistedEvent();
        EventAttachment badMime = newAttachment(ev.id, "image.png", 1024L, "image/png");
        badMime.persist();
        assertThrows(PersistenceException.class, em::flush,
                "DB CHECK event_attachments_mime_check must reject image/png");
    }
}
