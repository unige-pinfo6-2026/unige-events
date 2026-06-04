package ch.unige.events.event.attachment.dto;

import ch.unige.events.event.attachment.entity.EventAttachment;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * SCRUM-149 follow-up — pure unit tests for {@link AttachmentDTO#from}.
 *
 * <p>Pins the {@code downloadUrl} format ({@code /api/events/{eventId}/
 * attachments/{id}/download}) so a rename of the resource path can't
 * silently break the frontend (which uses this URL directly as
 * {@code <a href>}).
 *
 * <p>{@code @QuarkusTest} is required — event-service uses
 * {@code quarkus-jacoco}, which only tracks classes loaded through the
 * QuarkusClassLoader. A plain JUnit class would execute and pass but
 * leave the DTO reporting near-zero coverage in the Sonar new-code gate.
 */
@QuarkusTest
class AttachmentDTOTest {

    @Test
    void from_buildsDownloadUrlFromEventIdAndId() {
        EventAttachment entity = sampleEntity(42L, 7L);

        AttachmentDTO dto = AttachmentDTO.from(entity);

        assertEquals("/api/events/42/attachments/7/download", dto.downloadUrl());
    }

    @Test
    void from_copiesAllScalarFieldsVerbatim() {
        UUID uploader = UUID.randomUUID();
        LocalDateTime uploadedAt = LocalDateTime.of(2025, Month.JANUARY, 1, 12, 0);
        EventAttachment entity = sampleEntity(10L, 99L);
        entity.fileName = "deck.pdf";
        entity.fileUrl = "http://minio:9000/bucket/event-attachments/abc.pdf";
        entity.fileSize = 4096L;
        entity.mimeType = "application/pdf";
        entity.uploadedById = uploader;
        entity.uploadedAt = uploadedAt;

        AttachmentDTO dto = AttachmentDTO.from(entity);

        assertEquals(99L, dto.id());
        assertEquals("deck.pdf", dto.fileName());
        assertEquals("http://minio:9000/bucket/event-attachments/abc.pdf", dto.fileUrl());
        assertEquals(4096L, dto.fileSize());
        assertEquals("application/pdf", dto.mimeType());
        assertSame(uploader, dto.uploadedById());
        assertSame(uploadedAt, dto.uploadedAt());
    }

    @Test
    void from_keepsFileUrlDistinctFromDownloadUrl_documentingTheSplit() {
        // Regression guard for SCRUM-149 — the two URLs are deliberately
        // different things : fileUrl is internal-only (used by backend
        // cleanup), downloadUrl is the public same-origin endpoint.
        EventAttachment entity = sampleEntity(1L, 1L);
        AttachmentDTO dto = AttachmentDTO.from(entity);
        assertEquals(dto.fileUrl().startsWith("http"), true);
        assertEquals(dto.downloadUrl().startsWith("/api/"), true);
    }

    private static EventAttachment sampleEntity(Long eventId, Long id) {
        EventAttachment a = new EventAttachment();
        a.id = id;
        a.eventId = eventId;
        a.fileName = "x.pdf";
        a.fileUrl = "http://minio:9000/bucket/event-attachments/" + id + ".pdf";
        a.fileSize = 1L;
        a.mimeType = "application/pdf";
        a.uploadedById = UUID.randomUUID();
        a.uploadedAt = LocalDateTime.of(2025, Month.JANUARY, 1, 12, 0);
        return a;
    }
}
