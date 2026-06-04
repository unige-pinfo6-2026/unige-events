package ch.unige.events.event.coorganizer.dto;

import ch.unige.events.event.coorganizer.entity.EventCoOrganizer;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.domain.enums.CoOrganizerStatus;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Annotated {@code @QuarkusTest} so the executed {@code CoOrganizerDTO.from}
 * bytecode (including the null-user branch) is captured by quarkus-jacoco.
 */
@QuarkusTest
@SuppressWarnings("java:S100")
class CoOrganizerDTOTest {

    @Test
    void canonicalConstructor_keepsAllFields() {
        UUID userId = UUID.randomUUID();
        LocalDateTime invitedAt = LocalDateTime.of(2026, Month.MAY, 1, 12, 0);
        CoOrganizerDTO dto = new CoOrganizerDTO(7L, userId, "Alice",
                "https://avatar", CoOrganizerStatus.ACCEPTED, invitedAt);

        assertEquals(7L, dto.id());
        assertEquals(userId, dto.userId());
        assertEquals("Alice", dto.displayName());
        assertEquals("https://avatar", dto.avatarUrl());
        assertEquals(CoOrganizerStatus.ACCEPTED, dto.status());
        assertEquals(invitedAt, dto.invitedAt());
    }

    @Test
    void from_withUser_populatesDisplayNameAndAvatar() {
        EventCoOrganizer entity = new EventCoOrganizer();
        entity.id = 11L;
        entity.userId = UUID.randomUUID();
        entity.status = CoOrganizerStatus.PENDING;
        entity.invitedAt = LocalDateTime.of(2026, Month.MAY, 2, 10, 0);
        UserPublicResponse user = UserPublicResponse.anonymous(
                entity.userId, "Bob", "https://avatar/bob");

        CoOrganizerDTO dto = CoOrganizerDTO.from(entity, user);

        assertEquals(11L, dto.id());
        assertEquals(entity.userId, dto.userId());
        assertEquals("Bob", dto.displayName());
        assertEquals("https://avatar/bob", dto.avatarUrl());
        assertEquals(CoOrganizerStatus.PENDING, dto.status());
        assertEquals(entity.invitedAt, dto.invitedAt());
    }

    @Test
    void from_withNullUser_yieldsNullNameAndAvatar() {
        EventCoOrganizer entity = new EventCoOrganizer();
        entity.id = 12L;
        entity.userId = UUID.randomUUID();
        entity.status = CoOrganizerStatus.DECLINED;
        entity.invitedAt = LocalDateTime.of(2026, Month.MAY, 3, 9, 0);

        CoOrganizerDTO dto = CoOrganizerDTO.from(entity, null);

        assertEquals(12L, dto.id());
        assertNull(dto.displayName());
        assertNull(dto.avatarUrl());
        assertEquals(CoOrganizerStatus.DECLINED, dto.status());
    }

    @Test
    void recordEqualsAndHashCode_canonicalContract() {
        UUID uid = UUID.randomUUID();
        LocalDateTime t = LocalDateTime.of(2026, Month.MAY, 1, 12, 0);
        CoOrganizerDTO a = new CoOrganizerDTO(1L, uid, "X", null, CoOrganizerStatus.ACCEPTED, t);
        CoOrganizerDTO b = new CoOrganizerDTO(1L, uid, "X", null, CoOrganizerStatus.ACCEPTED, t);
        CoOrganizerDTO c = new CoOrganizerDTO(2L, uid, "X", null, CoOrganizerStatus.ACCEPTED, t);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
