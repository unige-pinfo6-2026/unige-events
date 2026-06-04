package ch.unige.events.engagement.attendance.dto;

import ch.unige.events.engagement.attendance.entity.Attendance;
import ch.unige.events.shared.domain.dto.AttendanceDTO;
import ch.unige.events.shared.domain.dto.AttendeeProjection;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.domain.enums.AttendanceStatus;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@code @QuarkusTest} so quarkus-jacoco credits the executed
 * {@link AttendanceDTOMapper} bytecode (the id-only {@code from(Attendance)}
 * factory among them) — plain unit tests do not contribute coverage.
 */
@QuarkusTest
class AttendanceDTOMapperTest {

    @Test
    void from_attendanceWithoutEnrichment_keepsAllIdFieldsButNullDisplayAndAvatar() {
        UUID userId = UUID.randomUUID();
        LocalDateTime created = LocalDateTime.of(2026, 5, 1, 12, 0);
        Attendance a = new Attendance();
        a.id = 7L;
        a.userId = userId;
        a.eventId = 42L;
        a.status = AttendanceStatus.ATTENDING;
        a.createdAt = created;

        AttendanceDTO dto = AttendanceDTOMapper.from(a);

        assertEquals(7L, dto.id());
        assertEquals(userId, dto.userId());
        assertEquals(42L, dto.eventId());
        assertEquals(AttendanceStatus.ATTENDING, dto.status());
        assertEquals(created, dto.createdAt());
        assertNull(dto.displayName());
        assertNull(dto.avatarUrl());
    }

    @Test
    void from_attendanceWithUser_enrichesDisplayNameAndAvatar() {
        UUID userId = UUID.randomUUID();
        Attendance a = new Attendance();
        a.id = 1L;
        a.userId = userId;
        a.eventId = 99L;
        a.status = AttendanceStatus.WAITLISTED;
        a.createdAt = LocalDateTime.of(2025, 1, 1, 12, 0);

        UserPublicResponse user = new UserPublicResponse(
            userId, "Alice", null, null, null, null,
            "https://avatars/alice.png", null, 0L, 0L, null);

        AttendanceDTO dto = AttendanceDTOMapper.from(a, user);

        assertEquals("Alice", dto.displayName());
        assertEquals("https://avatars/alice.png", dto.avatarUrl());
        assertEquals(AttendanceStatus.WAITLISTED, dto.status());
    }

    @Test
    void from_attendanceWithNullUser_keepsDisplayAndAvatarNull() {
        Attendance a = new Attendance();
        a.id = 1L;
        a.userId = UUID.randomUUID();
        a.eventId = 99L;
        a.status = AttendanceStatus.ATTENDING;
        a.createdAt = LocalDateTime.of(2025, 1, 1, 12, 0);

        AttendanceDTO dto = AttendanceDTOMapper.from(a, null);

        assertNull(dto.displayName());
        assertNull(dto.avatarUrl());
    }

    // ──────────────────────────────────────────────────────────────────
    // fromWithPrivacy(...) — SCRUM-S7
    // ──────────────────────────────────────────────────────────────────

    private static Attendance row(UUID userId) {
        Attendance a = new Attendance();
        a.id = 100L;
        a.userId = userId;
        a.eventId = 42L;
        a.status = AttendanceStatus.ATTENDING;
        a.createdAt = LocalDateTime.of(2026, 5, 14, 10, 0);
        return a;
    }

    @Test
    void fromWithPrivacy_organizerView_alwaysExposesIdentity() {
        UUID uid = UUID.randomUUID();
        Attendance a = row(uid);

        // Private profile → organizer still sees real identity.
        AttendeeProjection priv = new AttendeeProjection(uid, "Alice", "/avatars/a.png", false);
        AttendanceDTO dto = AttendanceDTOMapper.fromWithPrivacy(a, priv, true, false);

        assertEquals(uid, dto.userId());
        assertEquals("Alice", dto.displayName());
        assertEquals("/avatars/a.png", dto.avatarUrl());
    }

    @Test
    void fromWithPrivacy_nonOrganizerView_publicProfile_exposesIdentity() {
        UUID uid = UUID.randomUUID();
        Attendance a = row(uid);
        AttendeeProjection pub = new AttendeeProjection(uid, "Bob", "/avatars/b.png", true);

        AttendanceDTO dto = AttendanceDTOMapper.fromWithPrivacy(a, pub, false, false);

        assertEquals(uid, dto.userId());
        assertEquals("Bob", dto.displayName());
        assertEquals("/avatars/b.png", dto.avatarUrl());
    }

    @Test
    void fromWithPrivacy_nonOrganizerView_privateProfile_anonymizesEverythingIncludingUserId() {
        UUID uid = UUID.randomUUID();
        Attendance a = row(uid);
        AttendeeProjection priv = new AttendeeProjection(uid, "Carol", "/avatars/c.png", false);

        AttendanceDTO dto = AttendanceDTOMapper.fromWithPrivacy(a, priv, false, false);

        // Anonymized contract — nothing exposed about the participant.
        assertNull(dto.userId(), "userId must be nulled to prevent /users/{id} de-anonymization probe");
        assertNull(dto.displayName());
        assertNull(dto.avatarUrl());
        // Non-identity fields preserved (rendering + key need them).
        assertEquals(100L, dto.id());
        assertEquals(42L, dto.eventId());
        assertEquals(AttendanceStatus.ATTENDING, dto.status());
    }

    @Test
    void fromWithPrivacy_nonOrganizerView_privateProfile_callerFollows_exposesIdentity() {
        // An accepted follower of a private attendee sees their real identity
        // (consistent with seeing that private account's participations).
        UUID uid = UUID.randomUUID();
        Attendance a = row(uid);
        AttendeeProjection priv = new AttendeeProjection(uid, "Dana", "/avatars/d.png", false);

        AttendanceDTO dto = AttendanceDTOMapper.fromWithPrivacy(a, priv, false, true);

        assertEquals(uid, dto.userId());
        assertEquals("Dana", dto.displayName());
        assertEquals("/avatars/d.png", dto.avatarUrl());
    }

    @Test
    void fromWithPrivacy_nullProjection_anonymizesRegardlessOfRole() {
        Attendance a = row(UUID.randomUUID());

        AttendanceDTO asOrganizer = AttendanceDTOMapper.fromWithPrivacy(a, null, true, false);
        AttendanceDTO asNonOrganizer = AttendanceDTOMapper.fromWithPrivacy(a, null, false, false);

        for (AttendanceDTO dto : new AttendanceDTO[]{asOrganizer, asNonOrganizer}) {
            assertNull(dto.userId());
            assertNull(dto.displayName());
            assertNull(dto.avatarUrl());
        }
    }
}
