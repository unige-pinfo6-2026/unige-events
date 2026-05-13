package ch.unige.events.user.dto;

import ch.unige.events.shared.domain.enums.FollowStatus;
import ch.unige.events.user.entity.User;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

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
}
