package ch.unige.events.engagement.test;

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
import ch.unige.events.shared.kafka.events.EventBannedEvent;
import ch.unige.events.shared.kafka.events.FollowLifecycleEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage-focused exercises for the small shared classes (enums, DTO
 * records, factory helpers) that are reachable from engagement-service's
 * runtime classpath but otherwise had no in-module test. These are pure
 * static-method or canonical-record calls — none of them need Quarkus
 * machinery, so the test class is a plain JUnit test.
 */
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
        // Visit every enum value so jacoco marks the synthetic
        // values()/valueOf() bodies as covered.
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
    }

    @Test
    void dtoRecords_canonicalConstructors() {
        UUID id = UUID.randomUUID();
        AttendanceDTO a = new AttendanceDTO(1L, id, 2L, AttendanceStatus.ATTENDING,
                LocalDateTime.of(2025, Month.JANUARY, 1, 12, 0), "name", null);
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
                CoOrganizerStatus.ACCEPTED, LocalDateTime.of(2025, Month.JANUARY, 1, 12, 0));
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
    }
}
