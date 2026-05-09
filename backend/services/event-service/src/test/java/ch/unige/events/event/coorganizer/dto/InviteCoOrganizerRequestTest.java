package ch.unige.events.event.coorganizer.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SuppressWarnings("java:S100")
class InviteCoOrganizerRequestTest {

    @Test
    void canonicalConstructor_storesUserId() {
        UUID userId = UUID.randomUUID();
        InviteCoOrganizerRequest req = new InviteCoOrganizerRequest(userId);
        assertEquals(userId, req.userId());
    }

    @Test
    void canonicalConstructor_acceptsNullUserId_validatedAtBoundaryNotConstructor() {
        // The @NotNull annotation runs at JAX-RS validation time, not in
        // the canonical constructor — record itself accepts null.
        InviteCoOrganizerRequest req = new InviteCoOrganizerRequest(null);
        assertNull(req.userId());
    }

    @Test
    void recordEqualsAndHashCode_canonicalContract() {
        UUID id = UUID.randomUUID();
        InviteCoOrganizerRequest a = new InviteCoOrganizerRequest(id);
        InviteCoOrganizerRequest b = new InviteCoOrganizerRequest(id);
        InviteCoOrganizerRequest c = new InviteCoOrganizerRequest(UUID.randomUUID());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
