package ch.unige.events.event.attachment.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Owned by event-service. SCRUM-148 (Décision P).
 *
 * <p>FK {@code event_id → events(id) ON DELETE CASCADE} : local FK (both
 * tables live in {@code postgres-event}). When a row in {@code events} is
 * hard-deleted (post-CANCELLED via {@code EventService.delete()}), all its
 * attachments are garbage-collected automatically. {@code EventService.delete()}
 * ALSO does an explicit S3 cleanup pass (best-effort, hors-transaction —
 * Décision T) so the bucket objects don't leak.
 *
 * <p>No FK on {@code uploaded_by_id} : DB-per-service strict (cf.
 * {@code backend/AGENTS.md} — piège #8). The UUID consistency to
 * {@code postgres-user.users.id} is application-level.
 *
 * <p>The {@code event_attachments_size_check} CHECK pins the max file size
 * at 10 MiB (10485760 bytes). Going above is a 422
 * {@code attachment_invalid_size} from the service layer ; the DB CHECK is
 * the last line of defense in case of a bypass.
 *
 * <p>The {@code event_attachments_mime_check} CHECK pins the MIME whitelist
 * to PDF / DOC / DOCX / XLSX (Décision P).
 */
@Entity
@Table(
    name = "event_attachments",
    indexes = {
        @Index(name = "idx_event_attachment_event", columnList = "event_id")
    }
)
public class EventAttachment extends PanacheEntity {

    @Column(name = "event_id", nullable = false)
    public Long eventId;

    @Column(name = "file_name", nullable = false, length = 255)
    public String fileName;

    @Column(name = "file_url", nullable = false, columnDefinition = "TEXT")
    public String fileUrl;

    @Column(name = "file_size", nullable = false)
    public Long fileSize;

    @Column(name = "mime_type", nullable = false, length = 128)
    public String mimeType;

    @Column(name = "uploaded_by_id", nullable = false)
    public UUID uploadedById;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    public LocalDateTime uploadedAt;

    @PrePersist
    public void prePersist() {
        if (uploadedAt == null) {
            uploadedAt = LocalDateTime.now();
        }
    }

    /**
     * Returns the attachments for the given event ordered by upload time
     * ascending — the natural display order on {@code EventDetailPage}.
     * Used by {@code EventService.getById} (Décision Q) and by
     * {@code EventService.delete()} cascade cleanup (Décision T).
     */
    public static List<EventAttachment> findByEvent(Long eventId) {
        return list("eventId = ?1 order by uploadedAt asc, id asc", eventId);
    }

    /**
     * Counts the attachments for the given event. Used by
     * {@code EventAttachmentService.upload} to enforce the cap of 5 per
     * event (Décision V).
     */
    public static long countByEvent(Long eventId) {
        return count("eventId = ?1", eventId);
    }
}
