package ch.unige.events.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsernameGeneratorTest {

    @Test
    void slug_basicLowercase() {
        assertEquals("alice.martin", UsernameGenerator.slug("Alice Martin"));
    }

    @Test
    void slug_asciiFoldsAccents() {
        assertEquals("francois.muller", UsernameGenerator.slug("François Müller"));
    }

    @Test
    void slug_stripsNonAllowedChars() {
        assertEquals("alice.martin", UsernameGenerator.slug("Alice  Martin!?"));
    }

    @Test
    void slug_trimsLeadingTrailingSeparators() {
        assertEquals("alice", UsernameGenerator.slug("  ...alice...  "));
    }

    @Test
    void slug_collapsesRepeatedDots() {
        assertEquals("alice.martin", UsernameGenerator.slug("Alice...Martin"));
    }

    @Test
    void slug_truncatesAt30Chars() {
        String input = "abcdefghijklmnopqrstuvwxyz0123456789";
        String s = UsernameGenerator.slug(input);
        assertTrue(s.length() <= 30);
        assertEquals("abcdefghijklmnopqrstuvwxyz0123", s);
    }

    @Test
    void slug_returnsNullWhenTooShort() {
        assertNull(UsernameGenerator.slug("ab"));
        assertNull(UsernameGenerator.slug(""));
        assertNull(UsernameGenerator.slug(null));
        assertNull(UsernameGenerator.slug("!!"));
    }

    @Test
    void baseSlug_fallsBackToFirstNameLastName() {
        assertEquals("jean.dupont",
                UsernameGenerator.baseSlug(null, "Jean", "Dupont"));
    }

    @Test
    void baseSlug_fallsBackToUserWhenAllNull() {
        assertEquals("user",
                UsernameGenerator.baseSlug(null, null, null));
    }

    @Test
    void baseSlug_fallsBackToUserWhenAllBlank() {
        assertEquals("user",
                UsernameGenerator.baseSlug("", "", ""));
    }

    @Test
    void firstAvailable_returnsBaseWhenFree() {
        Set<String> taken = new HashSet<>();
        assertEquals("alice", UsernameGenerator.firstAvailable("alice", taken::contains));
    }

    @Test
    void firstAvailable_appendsIncrementalSuffix() {
        Set<String> taken = Set.of("jean.dupont", "jean.dupont2", "jean.dupont3", "jean.dupont4");
        assertEquals("jean.dupont5",
                UsernameGenerator.firstAvailable("jean.dupont", taken::contains));
    }

    @Test
    void firstAvailable_skipsBlocklist() {
        assertFalse(UsernameGenerator.isReserved("alice"));
        Set<String> taken = new HashSet<>();
        // "me" is blocklisted; firstAvailable must skip and append a numeric suffix.
        String result = UsernameGenerator.firstAvailable("me", taken::contains);
        assertFalse(UsernameGenerator.BLOCKLIST.contains(result));
        assertTrue(result.startsWith("me"));
    }

    @Test
    void isReserved_matchesBlocklist() {
        for (String reserved : UsernameGenerator.BLOCKLIST) {
            assertTrue(UsernameGenerator.isReserved(reserved));
            assertTrue(UsernameGenerator.isReserved(reserved.toUpperCase()));
        }
        assertFalse(UsernameGenerator.isReserved("alice"));
    }
}
