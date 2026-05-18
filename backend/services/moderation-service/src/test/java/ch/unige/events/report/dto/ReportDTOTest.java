package ch.unige.events.report.dto;

import ch.unige.events.report.entity.Report;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.domain.enums.ReportReason;
import ch.unige.events.shared.domain.enums.ReportStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReportDTOTest {

    @Test
    void canonicalConstructor_pendingReport_keepsAllFields() {
        UUID reporterId = UUID.randomUUID();
        UUID reviewedBy = UUID.randomUUID();
        LocalDateTime created = LocalDateTime.of(2026, 5, 1, 12, 0);
        LocalDateTime reviewed = LocalDateTime.of(2026, 5, 2, 9, 30);

        ReportDTO dto = new ReportDTO(
                7L, 42L, null, "Some Event",
                reporterId, "Alice",
                ReportReason.SPAM, "Looks like spam",
                ReportStatus.PENDING, "needs review",
                created, reviewed, reviewedBy);

        assertEquals(7L, dto.id());
        assertEquals(42L, dto.eventId());
        assertEquals("Some Event", dto.eventTitle());
        assertEquals(reporterId, dto.reporterId());
        assertEquals("Alice", dto.reporterDisplayName());
        assertEquals(ReportReason.SPAM, dto.reason());
        assertEquals("Looks like spam", dto.description());
        assertEquals(ReportStatus.PENDING, dto.status());
        assertEquals("needs review", dto.moderationNote());
        assertEquals(created, dto.createdAt());
        assertEquals(reviewed, dto.reviewedAt());
        assertEquals(reviewedBy, dto.reviewedBy());
    }

    @Test
    void recordEqualsAndHashCode_canonicalContract() {
        UUID reporterId = UUID.randomUUID();
        LocalDateTime created = LocalDateTime.of(2026, 5, 1, 12, 0);

        ReportDTO a = new ReportDTO(1L, 1L, null, "T", reporterId, "X",
                ReportReason.FAKE, null, ReportStatus.PENDING, null,
                created, null, null);
        ReportDTO b = new ReportDTO(1L, 1L, null, "T", reporterId, "X",
                ReportReason.FAKE, null, ReportStatus.PENDING, null,
                created, null, null);
        ReportDTO c = new ReportDTO(2L, 1L, null, "T", reporterId, "X",
                ReportReason.FAKE, null, ReportStatus.PENDING, null,
                created, null, null);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void from_populatedEntityWithEnrichment_mapsAllFields() {
        Report r = new Report();
        r.id = 11L;
        r.eventId = 99L;
        r.reporterId = UUID.randomUUID();
        r.reason = ReportReason.INAPPROPRIATE;
        r.description = "abusive";
        r.status = ReportStatus.REVIEWED;
        r.moderationNote = "removed";
        r.createdAt = LocalDateTime.of(2026, 5, 1, 8, 0);
        r.reviewedAt = LocalDateTime.of(2026, 5, 1, 10, 0);
        r.reviewedById = UUID.randomUUID();

        EventDTO event = new EventDTO(
                99L, "Concert", null, null, null, null, null, null, null,
                UUID.randomUUID(), null, null, false, false, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null);
        UserPublicResponse reporter = UserPublicResponse.anonymous(
                r.reporterId, "Bob", null);

        ReportDTO dto = ReportDTO.from(r, event, reporter);

        assertEquals(11L, dto.id());
        assertEquals(99L, dto.eventId());
        assertEquals("Concert", dto.eventTitle());
        assertEquals(r.reporterId, dto.reporterId());
        assertEquals("Bob", dto.reporterDisplayName());
        assertEquals(ReportReason.INAPPROPRIATE, dto.reason());
        assertEquals("abusive", dto.description());
        assertEquals(ReportStatus.REVIEWED, dto.status());
        assertEquals("removed", dto.moderationNote());
        assertEquals(r.createdAt, dto.createdAt());
        assertEquals(r.reviewedAt, dto.reviewedAt());
        assertEquals(r.reviewedById, dto.reviewedBy());
    }

    @Test
    void from_nullEnrichment_leavesEventTitleAndDisplayNameNull() {
        Report r = new Report();
        r.id = 1L;
        r.eventId = 2L;
        r.reporterId = UUID.randomUUID();
        r.reason = ReportReason.OTHER;
        r.status = ReportStatus.PENDING;
        r.createdAt = LocalDateTime.now();

        ReportDTO dto = ReportDTO.from(r, null, null);

        assertNull(dto.eventTitle());
        assertNull(dto.reporterDisplayName());
        assertEquals(2L, dto.eventId());
        assertEquals(ReportReason.OTHER, dto.reason());
        assertEquals(ReportStatus.PENDING, dto.status());
        assertNull(dto.moderationNote());
        assertNull(dto.reviewedAt());
        assertNull(dto.reviewedBy());
    }
}
