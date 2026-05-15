package ch.unige.events.user.dto;

import ch.unige.events.shared.domain.enums.FollowStatus;
import ch.unige.events.user.entity.User;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicProfileViewTest {

    @Test
    void canonicalConstructor_populatedFields_keepsAllValues() {
        User user = new User();
        user.id = UUID.randomUUID();
        user.displayName = "Alice";

        PublicProfileView view = new PublicProfileView(user, 4L, 2L, FollowStatus.ACCEPTED);

        assertSame(user, view.user());
        assertEquals(4L, view.followerCount());
        assertEquals(2L, view.followingCount());
        assertEquals(FollowStatus.ACCEPTED, view.followStatus());
        assertFalse(view.restricted(), "4-arg overload defaults restricted=false");
    }

    @Test
    void anonymous_zerosCountsAndNullStatus() {
        User user = new User();
        user.id = UUID.randomUUID();
        user.displayName = "Bob";

        PublicProfileView view = PublicProfileView.anonymous(user);

        assertSame(user, view.user());
        assertEquals(0L, view.followerCount());
        assertEquals(0L, view.followingCount());
        assertNull(view.followStatus());
        assertFalse(view.restricted());
    }

    @Test
    void restricted_factory_flagsRestrictedAndZerosCounters() {
        // SCRUM-169 revision — private profile + non-self non-admin caller.
        User user = new User();
        user.id = UUID.randomUUID();
        user.username = "alice.handle";
        user.displayName = "Alice";

        PublicProfileView view = PublicProfileView.restricted(user);

        assertSame(user, view.user());
        assertTrue(view.restricted(),
                "restricted() factory must set the flag so the resource layer strips the payload");
        assertEquals(0L, view.followerCount());
        assertEquals(0L, view.followingCount());
        assertNull(view.followStatus());
    }

    @Test
    void recordEqualsAndHashCode_canonicalContract() {
        User user = new User();
        user.id = UUID.randomUUID();

        PublicProfileView a = new PublicProfileView(user, 1L, 2L, FollowStatus.PENDING);
        PublicProfileView b = new PublicProfileView(user, 1L, 2L, FollowStatus.PENDING);
        PublicProfileView c = new PublicProfileView(user, 1L, 2L, FollowStatus.ACCEPTED);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void recordEquals_restrictedFlagIsPartOfTheContract() {
        // Two views with otherwise identical components must NOT be equal
        // when one is restricted=true and the other is restricted=false.
        User user = new User();
        user.id = UUID.randomUUID();

        PublicProfileView open = new PublicProfileView(user, 0L, 0L, null, false);
        PublicProfileView locked = new PublicProfileView(user, 0L, 0L, null, true);

        assertNotEquals(open, locked);
    }
}
