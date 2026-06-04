package ch.unige.events.user.test;

import ch.unige.events.shared.domain.dto.AttendanceDTO;
import ch.unige.events.shared.domain.dto.CapacitySummary;
import ch.unige.events.shared.domain.dto.CoOrganizerCheck;
import ch.unige.events.shared.domain.dto.EventCoOrganizerDTO;
import ch.unige.events.shared.domain.dto.FollowCounts;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.domain.enums.AttendanceStatus;
import ch.unige.events.shared.domain.enums.CoOrganizerStatus;
import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.enums.Faculty;
import ch.unige.events.shared.domain.enums.FollowStatus;
import ch.unige.events.shared.domain.enums.RecurrenceFrequency;
import ch.unige.events.shared.domain.enums.ReportReason;
import ch.unige.events.shared.domain.enums.ReportStatus;
import ch.unige.events.shared.domain.projections.AttendanceCounts;
import ch.unige.events.shared.error.ApiErrors;
import ch.unige.events.shared.kafka.events.CoOrganizerEvent;
import ch.unige.events.shared.kafka.events.CommentCreatedEvent;
import ch.unige.events.shared.kafka.events.EventBannedEvent;
import ch.unige.events.shared.kafka.events.EventLifecycleEvent;
import ch.unige.events.shared.kafka.events.FollowLifecycleEvent;
import ch.unige.events.shared.ratelimit.RateLimitExceededException;
import ch.unige.events.shared.ratelimit.RateLimitExceptionMapper;
import ch.unige.events.shared.storage.FileTooLargeException;
import ch.unige.events.shared.storage.ImageFormat;
import ch.unige.events.shared.storage.InvalidFileTypeException;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage exercises for the small shared classes (enums, DTO records,
 * factory helpers) reachable from user-service's runtime classpath but
 * otherwise untouched by the in-module tests.
 */
@QuarkusTest
class SharedCoverageTest {

    @Test
    void apiErrors_factoriesProduceTypedExceptions() {
        assertEquals(400, ApiErrors.badRequest("e", "m").getResponse().getStatus());
        assertEquals(409, ApiErrors.conflict("e", "m").getResponse().getStatus());
        assertEquals(422, ApiErrors.unprocessable("e", "m").getResponse().getStatus());
        assertEquals(403, ApiErrors.forbidden("e", "m").getResponse().getStatus());
        assertEquals(404, ApiErrors.notFound("e", "m").getResponse().getStatus());
    }

    @Test
    void enums_valuesAndValueOf() {
        for (AttendanceStatus s : AttendanceStatus.values()) {
            assertNotNull(AttendanceStatus.valueOf(s.name()));
        }
        for (EventStatus s : EventStatus.values()) {
            assertNotNull(EventStatus.valueOf(s.name()));
        }
        for (EventCategory s : EventCategory.values()) {
            assertNotNull(EventCategory.valueOf(s.name()));
        }
        for (Faculty s : Faculty.values()) {
            assertNotNull(Faculty.valueOf(s.name()));
        }
        for (FollowStatus s : FollowStatus.values()) {
            assertNotNull(FollowStatus.valueOf(s.name()));
        }
        for (CoOrganizerStatus s : CoOrganizerStatus.values()) {
            assertNotNull(CoOrganizerStatus.valueOf(s.name()));
        }
        for (RecurrenceFrequency s : RecurrenceFrequency.values()) {
            assertNotNull(RecurrenceFrequency.valueOf(s.name()));
        }
        for (ReportReason s : ReportReason.values()) {
            assertNotNull(ReportReason.valueOf(s.name()));
        }
        for (ReportStatus s : ReportStatus.values()) {
            assertNotNull(ReportStatus.valueOf(s.name()));
        }
        for (CoOrganizerEvent.Type s : CoOrganizerEvent.Type.values()) {
            assertNotNull(CoOrganizerEvent.Type.valueOf(s.name()));
        }
        for (FollowLifecycleEvent.Type s : FollowLifecycleEvent.Type.values()) {
            assertNotNull(FollowLifecycleEvent.Type.valueOf(s.name()));
        }
        for (EventLifecycleEvent.Type s : EventLifecycleEvent.Type.values()) {
            assertNotNull(EventLifecycleEvent.Type.valueOf(s.name()));
        }
    }

    @Test
    void dtoRecords_canonicalConstructors() {
        UUID id = UUID.randomUUID();
        AttendanceDTO a = new AttendanceDTO(1L, id, 2L, AttendanceStatus.ATTENDING,
                LocalDateTime.of(2025, 1, 1, 12, 0), "name", null);
        assertEquals(1L, a.id());

        UserPublicResponse u = new UserPublicResponse(id, "X", null, null, null, null,
                null, null, 0L, 0L, null);
        assertNotNull(UserPublicResponse.anonymous(id, "X", null));
        assertEquals("X", u.displayName());

        FollowCounts fc = new FollowCounts(1L, 2L, FollowStatus.ACCEPTED);
        assertEquals(1L, fc.followers());

        CapacitySummary cs = new CapacitySummary(10, 5L, 0L);
        assertEquals(10, cs.capacity());

        CoOrganizerCheck cc = CoOrganizerCheck.yes();
        assertTrue(cc.accepted());
        assertNotNull(CoOrganizerCheck.no());

        EventCoOrganizerDTO ecd = new EventCoOrganizerDTO(1L, 2L, id, "Bob", null,
                CoOrganizerStatus.ACCEPTED, LocalDateTime.of(2025, 1, 1, 12, 0));
        assertEquals(CoOrganizerStatus.ACCEPTED, ecd.status());

        AttendanceCounts ac = new AttendanceCounts(1L, 2L, 0L);
        assertEquals(1L, ac.attending());
        assertNotNull(AttendanceCounts.empty());
    }

    @Test
    void kafkaEvents_recordContracts() {
        UUID actor = UUID.randomUUID();
        EventBannedEvent banned = EventBannedEvent.banned(1L, actor, "reason");
        assertNotNull(banned);

        CoOrganizerEvent co = CoOrganizerEvent.invited(1L, actor);
        assertEquals(CoOrganizerEvent.Type.INVITED, co.type());
        CoOrganizerEvent co2 = CoOrganizerEvent.accepted(1L, actor);
        assertEquals(CoOrganizerEvent.Type.ACCEPTED, co2.type());

        FollowLifecycleEvent fl = FollowLifecycleEvent.followed(actor, actor);
        assertEquals(FollowLifecycleEvent.Type.FOLLOWED, fl.type());
        FollowLifecycleEvent fl2 = FollowLifecycleEvent.followRequested(actor, actor);
        assertEquals(FollowLifecycleEvent.Type.REQUESTED, fl2.type());
        FollowLifecycleEvent fl3 = FollowLifecycleEvent.followAccepted(actor, actor);
        assertEquals(FollowLifecycleEvent.Type.ACCEPTED, fl3.type());

        CommentCreatedEvent ce = CommentCreatedEvent.created(1L, 2L, actor, null,
                "hi", "Event title");
        assertEquals(1L, ce.commentId());

        EventLifecycleEvent ele = EventLifecycleEvent.published(1L, actor);
        assertEquals(EventLifecycleEvent.Type.PUBLISHED, ele.type());
        assertEquals(EventLifecycleEvent.Type.CANCELLED, EventLifecycleEvent.cancelled(2L, actor).type());
        assertEquals(EventLifecycleEvent.Type.EXPIRED, EventLifecycleEvent.expired(3L, actor).type());
    }

    @Test
    void rateLimitExceededException_carriesRetryAfter() {
        RateLimitExceededException ex = new RateLimitExceededException("rate exceeded", 30L);
        assertEquals(30L, ex.getRetryAfterSeconds());
        assertEquals(429, ex.getResponse().getStatus());
    }

    @Test
    void rateLimitExceptionMapper_buildsResponseWithRetryAfterHeader() {
        RateLimitExceptionMapper mapper = new RateLimitExceptionMapper();
        Response response = mapper.toResponse(new RateLimitExceededException("rate", 42L));
        assertEquals(429, response.getStatus());
        assertEquals("42", response.getHeaderString("Retry-After"));
    }

    @Test
    void imageFormat_matches_validPng_returnsTrue() throws Exception {
        Path tmp = Files.createTempFile("test-png-", ".png");
        // PNG magic bytes
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
                                0x00, 0x00, 0x00, 0x00};
        Files.write(tmp, png);
        try {
            assertTrue(ImageFormat.matches(tmp, "image/png"));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void imageFormat_matches_validJpeg_returnsTrue() throws Exception {
        Path tmp = Files.createTempFile("test-jpg-", ".jpg");
        byte[] jpg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF,
                                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        Files.write(tmp, jpg);
        try {
            assertTrue(ImageFormat.matches(tmp, "image/jpeg"));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void imageFormat_matches_validGif_returnsTrue() throws Exception {
        Path tmp = Files.createTempFile("test-gif-", ".gif");
        byte[] gif = new byte[]{'G', 'I', 'F', '8', '9', 'a',
                                0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        Files.write(tmp, gif);
        try {
            assertTrue(ImageFormat.matches(tmp, "image/gif"));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void imageFormat_matches_validWebp_returnsTrue() throws Exception {
        Path tmp = Files.createTempFile("test-webp-", ".webp");
        byte[] webp = new byte[]{'R', 'I', 'F', 'F',
                                  0x00, 0x00, 0x00, 0x00,
                                  'W', 'E', 'B', 'P'};
        Files.write(tmp, webp);
        try {
            assertTrue(ImageFormat.matches(tmp, "image/webp"));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void imageFormat_matches_lyingMime_returnsFalse() throws Exception {
        // Reject SVG bytes claiming to be image/png (ISSUE-91 stored XSS).
        Path tmp = Files.createTempFile("svg-as-png-", ".png");
        Files.writeString(tmp, "<svg xmlns='...'></svg>");
        try {
            assertEquals(false, ImageFormat.matches(tmp, "image/png"));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void imageFormat_matches_unknownMime_returnsFalse() throws Exception {
        Path tmp = Files.createTempFile("test-unknown-", ".bin");
        Files.write(tmp, new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
                                     0x09, 0x0A, 0x0B, 0x0C});
        try {
            assertEquals(false, ImageFormat.matches(tmp, "application/octet-stream"));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void fileTooLargeException_carriesMessage() {
        FileTooLargeException ex = new FileTooLargeException("too big");
        assertEquals("too big", ex.getMessage());
    }

    @Test
    void invalidFileTypeException_carriesMessage() {
        InvalidFileTypeException ex = new InvalidFileTypeException("bad type");
        assertEquals("bad type", ex.getMessage());
    }
}
