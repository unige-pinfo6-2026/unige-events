package ch.unige.events.shared.domain.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Sentinel pinning the exact values + ordering of every shared enum.
 *
 * <p>Hibernate's {@code @Enumerated(STRING)} round-trips on the literal
 * name. If a value is renamed, removed, or re-ordered, every persisted
 * row referencing it would fail to hydrate after deploy. This test
 * catches such drifts at compile time + CI run time.
 */
class EnumValuesSentinelTest {

    @Test
    void eventStatus_values() {
        assertArrayEquals(
                new EventStatus[] { EventStatus.DRAFT, EventStatus.PUBLISHED, EventStatus.CANCELLED, EventStatus.EXPIRED, EventStatus.BANNED },
                EventStatus.values());
        assertEquals(5, EventStatus.values().length);
        assertEquals(EventStatus.DRAFT, EventStatus.valueOf("DRAFT"));
    }

    @Test
    void eventCategory_values() {
        assertArrayEquals(
                new EventCategory[] { EventCategory.ACADEMIC, EventCategory.SPORTS, EventCategory.CULTURAL, EventCategory.SOCIAL, EventCategory.CONFERENCE, EventCategory.OTHER },
                EventCategory.values());
        assertEquals(6, EventCategory.values().length);
    }

    @Test
    void faculty_values() {
        assertArrayEquals(
                new Faculty[] { Faculty.SCIENCES, Faculty.MEDICINE, Faculty.LETTERS, Faculty.SOCIAL_SCIENCES, Faculty.GSEM, Faculty.LAW, Faculty.THEOLOGY, Faculty.PSYCHOLOGY, Faculty.FTI },
                Faculty.values());
        assertEquals(9, Faculty.values().length);
    }

    @Test
    void attendanceStatus_values() {
        assertArrayEquals(
                new AttendanceStatus[] { AttendanceStatus.ATTENDING, AttendanceStatus.WAITLISTED },
                AttendanceStatus.values());
        assertEquals(2, AttendanceStatus.values().length);
    }

    @Test
    void coOrganizerStatus_values() {
        assertArrayEquals(
                new CoOrganizerStatus[] { CoOrganizerStatus.PENDING, CoOrganizerStatus.ACCEPTED, CoOrganizerStatus.DECLINED },
                CoOrganizerStatus.values());
        assertEquals(3, CoOrganizerStatus.values().length);
    }

    @Test
    void followStatus_values() {
        assertArrayEquals(
                new FollowStatus[] { FollowStatus.PENDING, FollowStatus.ACCEPTED },
                FollowStatus.values());
        assertEquals(2, FollowStatus.values().length);
    }

    @Test
    void recurrenceFrequency_values() {
        assertArrayEquals(
                new RecurrenceFrequency[] { RecurrenceFrequency.WEEKLY, RecurrenceFrequency.BIWEEKLY, RecurrenceFrequency.MONTHLY },
                RecurrenceFrequency.values());
        assertEquals(3, RecurrenceFrequency.values().length);
    }

    @Test
    void reportStatus_values() {
        assertArrayEquals(
                new ReportStatus[] { ReportStatus.PENDING, ReportStatus.REVIEWED, ReportStatus.DISMISSED },
                ReportStatus.values());
        assertEquals(3, ReportStatus.values().length);
    }

    @Test
    void reportReason_values() {
        assertArrayEquals(
                new ReportReason[] { ReportReason.SPAM, ReportReason.INAPPROPRIATE, ReportReason.FAKE, ReportReason.OTHER },
                ReportReason.values());
        assertEquals(4, ReportReason.values().length);
    }
}
