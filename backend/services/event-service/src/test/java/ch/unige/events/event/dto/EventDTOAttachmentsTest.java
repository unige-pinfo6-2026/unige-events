package ch.unige.events.event.dto;

import ch.unige.events.event.attachment.dto.AttachmentDTO;
import ch.unige.events.event.entity.Event;
import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * SCRUM-148 — sentinel for {@link EventDTO} asymmetric {@code attachments}
 * field (Décision Q).
 *
 * <p>The {@code attachments} contract :
 * <ul>
 *   <li>{@code EventDTO.from(...)} (all overloads) sets {@code attachments=null}
 *       — every listing / search / featured / me / duplicate path goes
 *       through this factory.</li>
 *   <li>{@code EventDTO.fromWithAttachments(...)} is the ONLY factory that
 *       populates the field — called solely from {@code EventService.getById}.</li>
 * </ul>
 *
 * <p>Standalone record test (no {@code @QuarkusTest}) — keeps the suite fast
 * and the assertion surface tight ; the cross-endpoint behavior is
 * exercised in integration tests (notably {@code EventServiceTest}).
 */
class EventDTOAttachmentsTest {

    private static Event makeEvent(Long id) {
        Event e = new Event();
        e.id = id;
        e.title = "T-" + id;
        e.description = "d";
        e.location = "L";
        e.startDate = LocalDateTime.now();
        e.endDate = LocalDateTime.now().plusDays(1);
        e.category = EventCategory.CONFERENCE;
        e.creatorId = UUID.randomUUID();
        e.status = EventStatus.PUBLISHED;
        return e;
    }

    @Test
    void from_withoutAttachments_returnsNullField() {
        EventDTO dto = EventDTO.from(makeEvent(1L), 0L, 0L, 0L, 0L, 0L);
        assertNull(dto.attachments(),
                "EventDTO.from(...) must leave attachments null — signal 'not loaded'");
    }

    @Test
    void from_withCoOrgFlag_stillNullAttachments() {
        // The cross-service variant of from() (with coOrganizerOf) must also
        // leave attachments null — it's used by the engagement/moderation
        // hop, not by getById.
        EventDTO dto = EventDTO.from(makeEvent(2L), 0L, 0L, 0L, 0L, 0L, Boolean.TRUE);
        assertNull(dto.attachments());
    }

    @Test
    void fromWithAttachments_populatesField() {
        AttachmentDTO a1 = new AttachmentDTO(1L, "a.pdf", "url1", "/api/events/3/attachments/1/download",
                100L, "application/pdf", UUID.randomUUID(), LocalDateTime.now());
        AttachmentDTO a2 = new AttachmentDTO(2L, "b.pdf", "url2", "/api/events/3/attachments/2/download",
                200L, "application/pdf", UUID.randomUUID(), LocalDateTime.now());
        List<AttachmentDTO> list = List.of(a1, a2);

        EventDTO dto = EventDTO.fromWithAttachments(makeEvent(3L),
                0L, 0L, 0L, 0L, 0L, null, list);

        assertEquals(2, dto.attachments().size());
        assertSame(list, dto.attachments(),
                "fromWithAttachments must hand the caller list straight through "
                        + "— no defensive copy needed, list is already from this server");
    }

    @Test
    void fromWithAttachments_emptyList_populatedNotNull() {
        EventDTO dto = EventDTO.fromWithAttachments(makeEvent(4L),
                0L, 0L, 0L, 0L, 0L, null, List.of());

        // Empty list is semantically different from null : "loaded and
        // empty" vs "not loaded". The asymmetry is the load-bearing point.
        assertEquals(0, dto.attachments().size());
        org.junit.jupiter.api.Assertions.assertNotNull(dto.attachments());
    }

    @Test
    void from_preservesAllOtherFields() {
        Event ev = makeEvent(5L);
        EventDTO dto = EventDTO.from(ev, 3L, 7L, 1L, 12L, 4L, Boolean.FALSE);
        assertEquals(5L, dto.id());
        assertEquals("T-5", dto.title());
        assertEquals(3L, dto.attendingCount());
        assertEquals(7L, dto.availableSpots());
        assertEquals(1L, dto.waitlistedCount());
        assertEquals(12L, dto.viewCount());
        assertEquals(4L, dto.interestedCount());
        assertEquals(Boolean.FALSE, dto.coOrganizerOf());
        assertNull(dto.attachments());
    }
}
