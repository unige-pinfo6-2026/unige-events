package ch.unige.events.shared.domain.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttendeeProjectionTest {

    @Test
    void recordExposesAllFields() {
        UUID id = UUID.randomUUID();
        AttendeeProjection p = new AttendeeProjection(id, "Alice", "/avatars/alice.png", true);

        assertEquals(id, p.id());
        assertEquals("Alice", p.displayName());
        assertEquals("/avatars/alice.png", p.avatarUrl());
        assertTrue(p.profilePublic());
    }

    @Test
    void privateProfileFlag_isCarried() {
        AttendeeProjection p = new AttendeeProjection(UUID.randomUUID(), "Bob", null, false);

        assertFalse(p.profilePublic());
        assertNull(p.avatarUrl());
    }
}
