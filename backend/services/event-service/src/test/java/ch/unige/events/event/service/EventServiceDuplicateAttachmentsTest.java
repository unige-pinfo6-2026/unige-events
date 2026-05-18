package ch.unige.events.event.service;

import ch.unige.events.event.attachment.entity.EventAttachment;
import ch.unige.events.event.dto.EventDTO;
import ch.unige.events.event.entity.Event;
import ch.unige.events.event.test.JwtTestContext;
import ch.unige.events.event.test.JwtTestHelper;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceSummary;
import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;

import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

/**
 * SCRUM-148 — sentinel for Décision AC : {@link EventService#duplicate}
 * MUST NOT copy attachments to the clone. The DRAFT clone always starts
 * empty so the organizer can re-upload only what is still relevant.
 *
 * <p>The test is intentionally narrow : it verifies the no-cascade rule
 * and the asymmetry on the returned {@code EventDTO.attachments} field.
 * The full duplicate contract (title dedup, +7 day shift, reset matrix)
 * is covered by {@code EventResourceDuplicateTest} (SCRUM-99 phase 1).
 */
@QuarkusTest
class EventServiceDuplicateAttachmentsTest {

    @Inject EventService service;
    @Inject EntityManager em;

    @InjectMock @RestClient EngagementServiceClient engagementClient;
    @InjectMock @RestClient UserServiceClient userClient;

    private UUID creatorId;

    @BeforeEach
    void setUp() {
        creatorId = UUID.randomUUID();
        JwtTestContext.set(JwtTestHelper.jwtFor(creatorId));
        lenient().when(engagementClient.getAttendanceSummary(anyLong()))
                .thenReturn(AttendanceSummary.of(0L, 0L));
        lenient().when(engagementClient.getAttendanceSummariesBulk(any()))
                .thenReturn(java.util.Map.of());
    }

    @AfterEach
    void clear() {
        JwtTestContext.clear();
    }

    private Event persistEventOwnedBy(UUID owner) {
        Event e = new Event();
        e.title = "Source-Event";
        e.description = "desc";
        e.location = "Geneva";
        e.startDate = LocalDateTime.now().plusDays(2).withNano(0);
        e.endDate = LocalDateTime.now().plusDays(2).plusHours(2).withNano(0);
        e.category = EventCategory.CONFERENCE;
        e.creatorId = owner;
        e.status = EventStatus.PUBLISHED;
        e.persist();
        em.flush();
        return e;
    }

    private EventAttachment persistAttachment(Long eventId, String label) {
        EventAttachment a = new EventAttachment();
        a.eventId = eventId;
        a.fileName = label + ".pdf";
        a.fileUrl = "http://s3/bucket/event-attachments/" + label + ".pdf";
        a.fileSize = 1024L;
        a.mimeType = "application/pdf";
        a.uploadedById = creatorId;
        a.persist();
        em.flush();
        return a;
    }

    @Test
    @TestTransaction
    void duplicate_doesNotCopyAttachments_cloneStartsEmpty() {
        Event source = persistEventOwnedBy(creatorId);
        persistAttachment(source.id, "deck-v1");
        persistAttachment(source.id, "deck-v2");
        persistAttachment(source.id, "deck-v3");

        EventDTO clone = service.duplicate(source.id, "auth0|" + creatorId, false);

        // Décision AC — clone DB row carries zero attachment rows.
        assertEquals(0L, EventAttachment.countByEvent(clone.id()),
                "Décision AC : the DRAFT clone must start with an empty attachment list");
        // Source attachments still intact — duplicate does NOT touch the source.
        assertEquals(3L, EventAttachment.countByEvent(source.id),
                "duplicate() must not mutate the source's attachments");
    }

    @Test
    @TestTransaction
    void duplicate_returnedDtoHasNullAttachments() {
        // Décision Q asymmetry — only getById populates the field. duplicate
        // returns EventDTO via the from() factory (not fromWithAttachments)
        // so attachments must surface as null on the wire.
        Event source = persistEventOwnedBy(creatorId);
        persistAttachment(source.id, "anything");

        EventDTO clone = service.duplicate(source.id, "auth0|" + creatorId, false);

        assertNull(clone.attachments(),
                "duplicate's returned DTO must carry attachments=null "
                        + "(Décision Q — only getById populates)");
    }

    @Test
    @TestTransaction
    void duplicate_sourceWithNoAttachments_cloneAlsoEmpty() {
        // Defensive — a source with zero attachments must not surface a
        // weird empty-list contract on the clone (still null on the wire).
        Event source = persistEventOwnedBy(creatorId);
        EventDTO clone = service.duplicate(source.id, "auth0|" + creatorId, false);

        assertEquals(0L, EventAttachment.countByEvent(clone.id()));
        assertNull(clone.attachments());
    }
}
