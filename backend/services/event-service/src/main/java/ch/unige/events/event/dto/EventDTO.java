package ch.unige.events.event.dto;

import ch.unige.events.event.attachment.dto.AttachmentDTO;
import ch.unige.events.event.entity.Event;
import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.enums.Faculty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * MASTER {@code EventDTO} for the event-service public surface.
 *
 * <p>FR — Variant maître. Frères dupliqués intentionnellement post-Décision E
 * dans {@code event.me.dto}, {@code event.favorite.dto},
 * {@code event.coorganizer.dto} pour découpler les contrats sub-domain. Tous
 * les variants portent le drapeau {@code coOrganizerOf} (SCRUM-136). La
 * séparation est un point de variation contrôlé prêt à diverger sans casser
 * les siblings. NE PAS consolider sans revisiter la spec — le mainteneur
 * suivant doit traiter ceci comme un contrat.
 *
 * <p>EN — Master variant. Sibling consumer-shape projections live in
 * {@code event.me.dto}, {@code event.favorite.dto}, {@code event.coorganizer.dto}
 * — each is intentionally a separate record so individual sub-domains can
 * tighten field nullability without affecting siblings. Every variant now
 * carries the SCRUM-136 cascade flag {@code coOrganizerOf}. Consolidation
 * was attempted and reverted to avoid regressing typing / coverage. DO NOT
 * consolidate without revisiting the spec — the next maintainer should treat
 * this as a contract.
 */
public record EventDTO(
        Long id,
        String title,
        String description,
        String location,
        LocalDateTime startDate,
        LocalDateTime endDate,
        EventCategory category,
        Faculty faculty,
        String bannerUrl,
        UUID creatorId,
        EventStatus status,
        Integer capacity,
        boolean allDay,
        boolean featured,
        LocalDateTime featuredAt,
        long attendingCount,
        Long availableSpots,
        long waitlistedCount,
        Long viewCount,
        Long interestedCount,
        String websiteUrl,
        String contactEmail,
        LocalDateTime registrationDeadline,
        List<String> tags,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long parentEventId,
        String recurrenceRule,
        Boolean coOrganizerOf,
        /**
         * SCRUM-148 — populated ONLY by {@code GET /events/{id}} (Décision Q).
         * All other endpoints returning an {@code EventDTO} (listings, search,
         * featured, me-favorites, me-events, duplicate, etc.) leave this
         * field {@code null} to signal "not loaded". The frontend consumes
         * it on {@code EventDetailPage} only.
         */
        List<AttachmentDTO> attachments
) {
    public static EventDTO from(
            Event event,
            long attendingCount,
            Long availableSpots,
            long waitlistedCount,
            Long viewCount,
            Long interestedCount
    ) {
        return from(event, attendingCount, availableSpots, waitlistedCount, viewCount, interestedCount, null);
    }

    public static EventDTO from(
            Event event,
            long attendingCount,
            Long availableSpots,
            long waitlistedCount,
            Long viewCount,
            Long interestedCount,
            Boolean coOrganizerOf
    ) {
        return build(event, attendingCount, availableSpots, waitlistedCount, viewCount,
                interestedCount, coOrganizerOf, null);
    }

    /**
     * Factory variant called only by {@code EventService.getById} so the
     * returned DTO carries the {@code attachments} list (Décision Q). Every
     * other code path uses {@link #from} and leaves {@code attachments}
     * {@code null} — explicit null signals "not loaded" rather than
     * "loaded and empty".
     */
    public static EventDTO fromWithAttachments(
            Event event,
            long attendingCount,
            Long availableSpots,
            long waitlistedCount,
            Long viewCount,
            Long interestedCount,
            Boolean coOrganizerOf,
            List<AttachmentDTO> attachments
    ) {
        return build(event, attendingCount, availableSpots, waitlistedCount, viewCount,
                interestedCount, coOrganizerOf, attachments);
    }

    private static EventDTO build(
            Event event,
            long attendingCount,
            Long availableSpots,
            long waitlistedCount,
            Long viewCount,
            Long interestedCount,
            Boolean coOrganizerOf,
            List<AttachmentDTO> attachments
    ) {
        return new EventDTO(
                event.id,
                event.title,
                event.description,
                event.location,
                event.startDate,
                event.endDate,
                event.category,
                event.faculty,
                event.bannerUrl,
                event.creatorId,
                event.status,
                event.capacity,
                event.allDay,
                event.featured,
                event.featuredAt,
                attendingCount,
                availableSpots,
                waitlistedCount,
                viewCount,
                interestedCount,
                event.websiteUrl,
                event.contactEmail,
                event.registrationDeadline,
                event.tags != null ? List.copyOf(event.tags) : List.of(),
                event.createdAt,
                event.updatedAt,
                event.parentEventId,
                event.recurrenceRule,
                coOrganizerOf,
                attachments
        );
    }
}
