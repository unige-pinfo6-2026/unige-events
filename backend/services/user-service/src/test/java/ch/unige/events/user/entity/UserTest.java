package ch.unige.events.user.entity;

import ch.unige.events.shared.domain.enums.Faculty;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class UserTest {

    @Test
    void defaultValues_initialState() {
        User user = new User();

        assertFalse(user.profilePublic);
        assertNotNull(user.createdAt);
        assertEquals(0L, user.version);
    }

    @Test
    void publicFields_areAssignableAndReadable() {
        User user = new User();
        UUID id = UUID.randomUUID();
        UUID calendarToken = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, Month.MARCH, 19, 12, 0, 0);

        user.id = id;
        user.auth0Id = "auth0|alice";
        user.email = "alice@example.com";
        user.displayName = "Alice";
        user.firstName = "First";
        user.lastName = "Last";
        user.faculty = Faculty.SCIENCES;
        user.studyLevel = "Bachelor";
        user.bio = "Student at UNIGE";
        user.interests = List.of("AI", "Football");
        user.avatarUrl = "https://cdn.example.com/avatar.png";
        user.bannerUrl = "https://cdn.example.com/banner.png";
        user.profilePublic = true;
        user.createdAt = createdAt;
        user.version = 7L;
        user.calendarToken = calendarToken;

        assertEquals(id, user.id);
        assertEquals("auth0|alice", user.auth0Id);
        assertEquals("alice@example.com", user.email);
        assertEquals("Alice", user.displayName);
        assertEquals("First", user.firstName);
        assertEquals("Last", user.lastName);
        assertEquals(Faculty.SCIENCES, user.faculty);
        assertEquals("Bachelor", user.studyLevel);
        assertEquals("Student at UNIGE", user.bio);
        assertEquals(List.of("AI", "Football"), user.interests);
        assertEquals("https://cdn.example.com/avatar.png", user.avatarUrl);
        assertEquals("https://cdn.example.com/banner.png", user.bannerUrl);
        assertTrue(user.profilePublic);
        assertEquals(createdAt, user.createdAt);
        assertEquals(7L, user.version);
        assertEquals(calendarToken, user.calendarToken);
    }

    // ─── SCRUM-145 — findByUsernames early-return / filter branches ──
    // These exercise the pure pre-Panache logic on the static finder. The
    // Panache `list(...)` branch is covered by the @QuarkusTest at
    // UserUsernamesInternalResourceTest ; here we only pin the short-
    // circuit paths so a future refactor that drops them gets caught.

    @Test
    void findByUsernames_null_returnsEmptyList() {
        assertEquals(Collections.emptyList(), User.findByUsernames(null));
    }

    @Test
    void findByUsernames_emptyCollection_returnsEmptyList() {
        assertEquals(Collections.emptyList(), User.findByUsernames(List.of()));
    }

    @Test
    void findByUsernames_allEntriesNullOrBlank_returnsEmptyListWithoutHittingPanache() {
        // The filter drops nulls + isBlank()s. With nothing left, the
        // method short-circuits BEFORE the JPQL call — so no Quarkus
        // runtime is needed.
        List<String> input = Arrays.asList(null, "", "   ", "\t\n ");
        assertEquals(Collections.emptyList(), User.findByUsernames(input));
    }

    // ─── findByUsername — null short-circuit branch ─────────────────

    @Test
    void findByUsername_null_returnsEmptyOptional() {
        assertEquals(Optional.empty(), User.findByUsername(null));
    }

    // ─── searchByUsernamePrefix — null / blank / non-positive guards ─

    @Test
    void searchByUsernamePrefix_nullPrefix_returnsEmpty() {
        assertEquals(Collections.emptyList(), User.searchByUsernamePrefix(null, 10, null));
    }

    @Test
    void searchByUsernamePrefix_blankPrefix_returnsEmpty() {
        assertEquals(Collections.emptyList(), User.searchByUsernamePrefix("   ", 10, null));
    }

    @Test
    void searchByUsernamePrefix_zeroLimit_returnsEmpty() {
        assertEquals(Collections.emptyList(), User.searchByUsernamePrefix("ali", 0, null));
    }
}
