package ch.unige.events.event.attachment.service;

import ch.unige.events.event.attachment.dto.AttachmentDTO;
import ch.unige.events.event.attachment.entity.EventAttachment;
import ch.unige.events.event.coorganizer.entity.EventCoOrganizer;
import ch.unige.events.event.entity.Event;
import ch.unige.events.event.test.JwtTestContext;
import ch.unige.events.event.test.JwtTestHelper;
import ch.unige.events.shared.domain.enums.CoOrganizerStatus;
import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.storage.DocumentFormat;
import ch.unige.events.shared.storage.FileStorageService;

import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SCRUM-148 — service-side coverage for {@link EventAttachmentService}
 * (Décisions V + T).
 *
 * <p>Drives the service against a real DevServices Postgres so the FK
 * cascade + the cap-of-5 query both run for real. The S3 backend is
 * mocked via {@link InjectMock @InjectMock} on
 * {@link FileStorageService} so no Minio is required.
 */
@QuarkusTest
class EventAttachmentServiceTest {

    @Inject EventAttachmentService service;
    @Inject EntityManager em;

    @InjectMock FileStorageService fileStorageService;

    private UUID callerId;
    private UUID creatorId;

    @BeforeEach
    void setUp() {
        callerId = UUID.randomUUID();
        creatorId = UUID.randomUUID();
        JwtTestContext.set(JwtTestHelper.jwtFor(callerId));
    }

    @AfterEach
    void clear() {
        JwtTestContext.clear();
    }

    private Event persistEvent(UUID creator) {
        Event e = new Event();
        e.title = "T-attach";
        e.description = "for attachment test";
        e.location = "Geneva";
        e.startDate = LocalDateTime.now().plusDays(1);
        e.endDate = LocalDateTime.now().plusDays(2);
        e.category = EventCategory.CONFERENCE;
        e.creatorId = creator;
        e.status = EventStatus.PUBLISHED;
        e.persist();
        em.flush();
        return e;
    }

    private EventAttachment persistAttachment(Long eventId, UUID uploader, String name) {
        EventAttachment a = new EventAttachment();
        a.eventId = eventId;
        a.fileName = name;
        a.fileUrl = "http://s3/bucket/event-attachments/" + UUID.randomUUID() + ".pdf";
        a.fileSize = 1024L;
        a.mimeType = "application/pdf";
        a.uploadedById = uploader;
        a.persist();
        em.flush();
        return a;
    }

    private FileUpload pdfUpload() {
        FileUpload f = org.mockito.Mockito.mock(FileUpload.class);
        when(f.fileName()).thenReturn("paper.pdf");
        when(f.contentType()).thenReturn("application/pdf");
        when(f.size()).thenReturn(1024L);
        return f;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // upload(...)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @TestTransaction
    void upload_byCreator_returnsAttachmentAndCallsSaveFile() {
        // Caller is the creator — permission grant via the cascade helper.
        Event ev = persistEvent(callerId);
        when(fileStorageService.saveFile(any(), eq("event-attachments"),
                eq(DocumentFormat.MAX_BYTES), eq(DocumentFormat.MIME_TO_EXTENSION),
                any()))
                .thenReturn("http://s3/bucket/event-attachments/abc.pdf");

        AttachmentDTO dto = service.upload(ev.id, pdfUpload(), false);

        assertNotNull(dto);
        assertEquals("paper.pdf", dto.fileName());
        assertEquals("http://s3/bucket/event-attachments/abc.pdf", dto.fileUrl());
        assertEquals(callerId, dto.uploadedById());
        assertEquals(1L, EventAttachment.countByEvent(ev.id));
        verify(fileStorageService).saveFile(any(), eq("event-attachments"),
                eq(DocumentFormat.MAX_BYTES), eq(DocumentFormat.MIME_TO_EXTENSION), any());
    }

    @Test
    @TestTransaction
    void upload_byAcceptedCoOrg_returnsAttachment() {
        Event ev = persistEvent(creatorId);
        EventCoOrganizer co = new EventCoOrganizer();
        co.eventId = ev.id;
        co.userId = callerId;
        co.status = CoOrganizerStatus.ACCEPTED;
        co.persist();
        em.flush();

        when(fileStorageService.saveFile(any(), anyString(), anyLong(), any(), any()))
                .thenReturn("http://s3/bucket/event-attachments/co.pdf");

        AttachmentDTO dto = service.upload(ev.id, pdfUpload(), false);
        assertEquals(callerId, dto.uploadedById());
    }

    @Test
    @TestTransaction
    void upload_byAdmin_returnsAttachment() {
        Event ev = persistEvent(creatorId);  // not the caller
        when(fileStorageService.saveFile(any(), anyString(), anyLong(), any(), any()))
                .thenReturn("http://s3/bucket/event-attachments/admin.pdf");

        AttachmentDTO dto = service.upload(ev.id, pdfUpload(), true);
        assertEquals(callerId, dto.uploadedById());
    }

    @Test
    @TestTransaction
    void upload_byNonOrganizer_throws403() {
        Event ev = persistEvent(creatorId);  // caller is neither creator nor admin
        FileUpload up = pdfUpload();

        assertThrows(ForbiddenException.class,
                () -> service.upload(ev.id, up, false));
    }

    @Test
    @TestTransaction
    void upload_unknownEvent_throws404() {
        FileUpload up = pdfUpload();
        assertThrows(NotFoundException.class,
                () -> service.upload(9_999_999L, up, false));
    }

    @Test
    @TestTransaction
    void upload_capExceeded_throws422() {
        Event ev = persistEvent(callerId);
        // Cap = 5 ; pre-populate 5 rows.
        for (int i = 0; i < 5; i++) {
            persistAttachment(ev.id, callerId, "file-" + i + ".pdf");
        }
        FileUpload up = pdfUpload();

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.upload(ev.id, up, false));
        assertEquals(422, ex.getResponse().getStatus());
        // The 6th upload must NOT call saveFile — short-circuit before S3.
        verify(fileStorageService, org.mockito.Mockito.never())
                .saveFile(any(), anyString(), anyLong(), any(), any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // delete(...)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @TestTransaction
    void delete_byCreator_purgesRowAndCallsS3Cleanup() {
        Event ev = persistEvent(callerId);
        EventAttachment a = persistAttachment(ev.id, UUID.randomUUID(), "doc.pdf");

        service.delete(ev.id, a.id, false);

        assertEquals(0L, EventAttachment.countByEvent(ev.id));
        verify(fileStorageService).deleteObject(eq(a.fileUrl));
    }

    @Test
    @TestTransaction
    void delete_byUploader_purgesRowEvenWhenNoLongerCoOrg() {
        // Decision V — the original uploader retains the right to delete
        // their own upload even after losing co-organizer status.
        Event ev = persistEvent(creatorId);  // someone else owns the event
        EventAttachment a = persistAttachment(ev.id, callerId, "mine.pdf");

        service.delete(ev.id, a.id, false);
        assertEquals(0L, EventAttachment.countByEvent(ev.id));
    }

    @Test
    @TestTransaction
    void delete_byAdmin_purgesRow() {
        Event ev = persistEvent(creatorId);
        EventAttachment a = persistAttachment(ev.id, UUID.randomUUID(), "doc.pdf");

        service.delete(ev.id, a.id, true);
        assertEquals(0L, EventAttachment.countByEvent(ev.id));
    }

    @Test
    @TestTransaction
    void delete_byNonAuthorized_throws403() {
        Event ev = persistEvent(creatorId);
        EventAttachment a = persistAttachment(ev.id, UUID.randomUUID(), "doc.pdf");

        assertThrows(ForbiddenException.class,
                () -> service.delete(ev.id, a.id, false));
    }

    @Test
    @TestTransaction
    void delete_unknownAttachment_throws404() {
        Event ev = persistEvent(callerId);
        assertThrows(NotFoundException.class,
                () -> service.delete(ev.id, 9_999_999L, false));
    }

    @Test
    @TestTransaction
    void delete_pathMismatch_throws404AntiOracle() {
        // attachmentId exists but belongs to another event — must return
        // 404, not 403, to avoid leaking the existence of the row to a
        // caller who shouldn't know about it.
        Event ev1 = persistEvent(callerId);
        Event ev2 = persistEvent(callerId);
        EventAttachment a = persistAttachment(ev2.id, callerId, "doc.pdf");

        assertThrows(NotFoundException.class,
                () -> service.delete(ev1.id, a.id, false));
        // The row remains intact — confirms the 404 short-circuit happens
        // before the .delete() call.
        assertEquals(1L, EventAttachment.countByEvent(ev2.id));
    }

    @Test
    @TestTransaction
    void delete_s3CleanupFails_dbDeleteStillCommits() {
        // Décision T : the storage layer swallows S3 failures with a WARN
        // log ; the DB delete is the authoritative side. If a future
        // refactor accidentally re-throws, this test catches it.
        Event ev = persistEvent(callerId);
        EventAttachment a = persistAttachment(ev.id, callerId, "doc.pdf");
        doThrow(new RuntimeException("transient s3 failure"))
                .when(fileStorageService).deleteObject(eq(a.fileUrl));

        // The service must surface no exception — FileStorageService.deleteObject
        // is contractually best-effort. Our test contract here is that no
        // exception bubbles AND the row is gone.
        try {
            service.delete(ev.id, a.id, false);
        } catch (RuntimeException unexpected) {
            // The current contract swallows storage failures via
            // FileStorageService.deleteObject ; if the mock made them surface,
            // the test setup is wrong. Re-throwing here is a fail-fast guard.
            throw new AssertionError(
                    "EventAttachmentService.delete must not propagate S3 cleanup failures", unexpected);
        }
        assertEquals(0L, EventAttachment.countByEvent(ev.id));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getByEvent(...)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @TestTransaction
    void getByEvent_returnsAttachmentsSortedByUploadedAt() {
        Event ev = persistEvent(callerId);
        EventAttachment first = persistAttachment(ev.id, callerId, "early.pdf");
        first.uploadedAt = LocalDateTime.now().minusMinutes(10);
        EventAttachment second = persistAttachment(ev.id, callerId, "late.pdf");
        second.uploadedAt = LocalDateTime.now();
        em.flush();

        List<AttachmentDTO> result = service.getByEvent(ev.id);
        assertEquals(2, result.size());
        assertEquals("early.pdf", result.get(0).fileName());
        assertEquals("late.pdf", result.get(1).fileName());
    }

    @Test
    @TestTransaction
    void getByEvent_emptyForUnknownEvent() {
        assertTrue(service.getByEvent(9_999_999L).isEmpty());
    }
}
