package ch.unige.events.event.service;

import ch.unige.events.event.attachment.entity.EventAttachment;
import ch.unige.events.event.entity.Event;
import ch.unige.events.event.test.JwtTestContext;
import ch.unige.events.event.test.JwtTestHelper;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceSummary;
import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.storage.FileStorageService;

import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * SCRUM-148 — cascade cleanup sentinel for
 * {@link EventService#delete} (Décision T). Asserts :
 * <ul>
 *   <li>{@code event_attachments} rows are purged before the event row.</li>
 *   <li>{@link FileStorageService#deleteObject(String)} is invoked once
 *       per captured URL (best-effort hors-tx).</li>
 *   <li>An S3 cleanup failure does NOT roll back the DB delete — the
 *       storage layer swallows the exception.</li>
 *   <li>An event with no attachments triggers zero S3 cleanup calls (no
 *       wasted call, no surprising WARN log).</li>
 *   <li>A PUBLISHED event (not CANCELLED) refuses the delete with 409
 *       and leaves the attachment rows intact.</li>
 * </ul>
 */
@QuarkusTest
class EventServiceDeleteCascadeAttachmentsTest {

    @Inject EventService service;
    @Inject EntityManager em;

    @InjectMock FileStorageService fileStorageService;
    @InjectMock @RestClient EngagementServiceClient engagementClient;
    @InjectMock @RestClient UserServiceClient userClient;

    private UUID creatorId;

    @BeforeEach
    void setUp() {
        creatorId = UUID.randomUUID();
        JwtTestContext.set(JwtTestHelper.jwtFor(creatorId));
        lenient().when(engagementClient.getAttendanceSummary(anyLong()))
                .thenReturn(AttendanceSummary.of(0L, 0L));
    }

    @AfterEach
    void clear() {
        JwtTestContext.clear();
    }

    private Event persistEvent(EventStatus status) {
        Event e = new Event();
        e.title = "T-delete";
        e.description = "d";
        e.location = "L";
        e.startDate = LocalDateTime.of(2999, Month.JANUARY, 1, 0, 0).plusDays(1);
        e.endDate = LocalDateTime.of(2999, Month.JANUARY, 1, 0, 0).plusDays(2);
        e.category = EventCategory.CONFERENCE;
        e.creatorId = creatorId;
        e.status = status;
        e.persist();
        em.flush();
        return e;
    }

    private EventAttachment persistAttachment(Long eventId, String key) {
        EventAttachment a = new EventAttachment();
        a.eventId = eventId;
        a.fileName = key + ".pdf";
        a.fileUrl = "http://s3/bucket/event-attachments/" + key + ".pdf";
        a.fileSize = 1024L;
        a.mimeType = "application/pdf";
        a.uploadedById = creatorId;
        a.persist();
        em.flush();
        return a;
    }

    @Test
    @TestTransaction
    void delete_cancelledEventWithAttachments_purgesDbRowsAndCallsS3Cleanup() {
        Event ev = persistEvent(EventStatus.CANCELLED);
        persistAttachment(ev.id, "a");
        persistAttachment(ev.id, "b");
        persistAttachment(ev.id, "c");

        service.delete(ev.id, "auth0|" + creatorId);

        assertEquals(0L, EventAttachment.countByEvent(ev.id));
        verify(fileStorageService, times(3)).deleteObject(anyString());
    }

    @Test
    @TestTransaction
    void delete_s3CleanupFailure_doesNotRollback() {
        Event ev = persistEvent(EventStatus.CANCELLED);
        persistAttachment(ev.id, "fail");

        // FileStorageService.deleteObject swallows the failure internally
        // — explicitly throwing here verifies the @Transactional wrapper
        // does not propagate. If a refactor accidentally re-throws, the
        // tx rolls back and the test fails (event_attachments stays).
        doThrow(new RuntimeException("simulated S3 failure"))
                .when(fileStorageService).deleteObject(anyString());

        try {
            service.delete(ev.id, "auth0|" + creatorId);
        } catch (RuntimeException unexpected) {
            throw new AssertionError(
                    "EventService.delete must not propagate S3 cleanup failures — "
                            + "FileStorageService.deleteObject is contractually best-effort",
                    unexpected);
        }
        // FileStorageService.deleteObject swallows ; the row is gone.
        assertEquals(0L, EventAttachment.countByEvent(ev.id));
    }

    @Test
    @TestTransaction
    void delete_eventWithNoAttachments_noS3CleanupCalls() {
        Event ev = persistEvent(EventStatus.CANCELLED);
        // No attachments persisted.
        service.delete(ev.id, "auth0|" + creatorId);
        verify(fileStorageService, never()).deleteObject(anyString());
    }

    @Test
    @TestTransaction
    void delete_publishedEvent_refusesAndKeepsAttachments() {
        Event ev = persistEvent(EventStatus.PUBLISHED);
        persistAttachment(ev.id, "keep");

        assertThrows(WebApplicationException.class,
                () -> service.delete(ev.id, "auth0|" + creatorId));

        // Attachment row intact, no S3 cleanup attempted.
        assertEquals(1L, EventAttachment.countByEvent(ev.id));
        verify(fileStorageService, never()).deleteObject(anyString());
    }

    @Test
    @TestTransaction
    void delete_capturesUrlsBeforePurgeNotAfter() {
        // Guards the Décision T ordering : if the JPQL DELETE ran before
        // the findByEvent, the captured list would be empty and the S3
        // cleanup would silently no-op. The test pre-records the expected
        // URLs and verifies each one is passed to deleteObject by exact
        // string match (mockito eq).
        Event ev = persistEvent(EventStatus.CANCELLED);
        EventAttachment a1 = persistAttachment(ev.id, "first");
        EventAttachment a2 = persistAttachment(ev.id, "second");
        List<String> expectedUrls = List.of(a1.fileUrl, a2.fileUrl);

        service.delete(ev.id, "auth0|" + creatorId);

        for (String url : expectedUrls) {
            verify(fileStorageService).deleteObject(url);
        }
    }
}
