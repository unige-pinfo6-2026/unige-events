package ch.unige.events.user.service;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SCRUM-169 — pure unit tests for the slug / blocklist / suffix logic. The
 * cases here mirror the SQL back-fill in {@code V3__add_user_username.sql} —
 * any divergence between Java and PL/pgSQL would silently corrupt usernames at
 * scale, so we pin the shared semantics in one place.
 */
@QuarkusTest
class UsernameGeneratorTest {

    private static final Predicate<String> NEVER_TAKEN = c -> false;

    @Test
    void slugify_simpleDisplayName_lowercaseDots() {
        // Sentinel: "Jean Dupont" -> "jean.dupont"
        assertEquals("jean.dupont", UsernameGenerator.slugify("Jean Dupont", null, null));
    }

    @Test
    void slugify_accentsAreAsciiFolded() {
        // Sentinel: ASCII fold is the contract — accents stripped via NFD.
        assertEquals("francois.muller", UsernameGenerator.slugify("François Müller", null, null));
        assertEquals("dorde.petrovic", UsernameGenerator.slugify("Đorđe Petrović", null, null));
    }

    @Test
    void slugify_droppedCharsAreSilent() {
        // O'Brien -> obrien (apostrophe dropped, no warning, no exception).
        assertEquals("obrien", UsernameGenerator.slugify("O'Brien", null, null));
    }

    @Test
    void slugify_collapsesMultipleSpacesAndDots() {
        assertEquals("jean.dupont", UsernameGenerator.slugify("  Jean   Dupont  ", null, null));
        assertEquals("a.b.c", UsernameGenerator.slugify("a...b....c", null, null));
    }

    @Test
    void slugify_trimsLeadingAndTrailingPunctuation() {
        assertEquals("foo", UsernameGenerator.slugify("...foo...", null, null));
        assertEquals("foo.bar", UsernameGenerator.slugify("-foo.bar_", null, null));
    }

    @Test
    void slugify_blankDisplayName_fallsBackToFirstLast() {
        assertEquals("marie.curie", UsernameGenerator.slugify("", "Marie", "Curie"));
        assertEquals("marie.curie", UsernameGenerator.slugify(null, "Marie", "Curie"));
        assertEquals("marie.curie", UsernameGenerator.slugify("   ", "Marie", "Curie"));
    }

    @Test
    void slugify_onlyFirstName_skipsTrailingDot() {
        assertEquals("marie", UsernameGenerator.slugify(null, "Marie", null));
        assertEquals("marie", UsernameGenerator.slugify(null, "Marie", ""));
    }

    @Test
    void slugify_onlyLastName_skipsLeadingDot() {
        assertEquals("curie", UsernameGenerator.slugify(null, null, "Curie"));
        assertEquals("curie", UsernameGenerator.slugify(null, "", "Curie"));
    }

    @Test
    void slugify_allEmpty_userFallback() {
        assertEquals("user", UsernameGenerator.slugify(null, null, null));
        assertEquals("user", UsernameGenerator.slugify("", "", ""));
        assertEquals("user", UsernameGenerator.slugify("   ", "  ", "  "));
    }

    @Test
    void slugify_resultUnder3Chars_userFallback() {
        // "X" alone yields "x" which is < 3 chars -> fallback "user".
        assertEquals("user", UsernameGenerator.slugify("X", null, null));
        assertEquals("user", UsernameGenerator.slugify("ab", null, null));
        // "abc" stays as-is.
        assertEquals("abc", UsernameGenerator.slugify("abc", null, null));
    }

    @Test
    void slugify_truncatesAt30Chars() {
        String longName = "a".repeat(50);
        String slug = UsernameGenerator.slugify(longName, null, null);
        assertEquals(30, slug.length());
        assertEquals("a".repeat(30), slug);
    }

    @Test
    void slugify_truncationRetrimsTrailingPunctuation() {
        // A slug that would land exactly on a '.' at char 30 should have it stripped.
        // "abcdefghij" * 3 + "." (31 chars) -> truncate to 30 -> "abcdefghij" * 3 (already clean).
        // Construct a case where cut lands on '.': 29 chars + '.' + more
        // "aaa" (3) + "." (29th char) → 30 chars = "aaa..............aaa..........."
        String input = "a".repeat(29) + ".garbage";
        String slug = UsernameGenerator.slugify(input, null, null);
        assertTrue(slug.length() <= 30);
        assertFalse(slug.endsWith("."));
        assertFalse(slug.endsWith("_"));
        assertFalse(slug.endsWith("-"));
    }

    @Test
    void isValid_matchesPattern() {
        assertTrue(UsernameGenerator.isValid("jean.dupont"));
        assertTrue(UsernameGenerator.isValid("abc"));
        assertTrue(UsernameGenerator.isValid("a".repeat(30)));
        assertTrue(UsernameGenerator.isValid("a.b-c_d"));
        assertTrue(UsernameGenerator.isValid("123abc"));
    }

    @Test
    void isValid_rejectsInvalid() {
        assertFalse(UsernameGenerator.isValid(null));
        assertFalse(UsernameGenerator.isValid(""));
        assertFalse(UsernameGenerator.isValid("ab"));                // < 3
        assertFalse(UsernameGenerator.isValid("a".repeat(31)));      // > 30
        assertFalse(UsernameGenerator.isValid("Jean.Dupont"));       // uppercase
        assertFalse(UsernameGenerator.isValid("jean dupont"));       // space
        assertFalse(UsernameGenerator.isValid("jean@dupont"));       // @
        assertFalse(UsernameGenerator.isValid("françois"));          // non-ASCII
    }

    @Test
    void isReserved_caseInsensitiveMatch() {
        assertTrue(UsernameGenerator.isReserved("me"));
        assertTrue(UsernameGenerator.isReserved("ME"));
        assertTrue(UsernameGenerator.isReserved("Admin"));
        assertTrue(UsernameGenerator.isReserved("api"));
        assertTrue(UsernameGenerator.isReserved("login"));
        assertTrue(UsernameGenerator.isReserved("logout"));
        assertTrue(UsernameGenerator.isReserved("signup"));
        assertTrue(UsernameGenerator.isReserved("register"));
        assertTrue(UsernameGenerator.isReserved("settings"));

        assertFalse(UsernameGenerator.isReserved("user"));
        assertFalse(UsernameGenerator.isReserved("jean.dupont"));
        assertFalse(UsernameGenerator.isReserved(null));
    }

    @Test
    void resolveAvailable_noCollision_returnsBaseUnchanged() {
        assertEquals("jean.dupont", UsernameGenerator.resolveAvailable("jean.dupont", NEVER_TAKEN));
    }

    @Test
    void resolveAvailable_oneCollision_appendsSuffix2() {
        Set<String> taken = new HashSet<>(Set.of("jean.dupont"));
        assertEquals("jean.dupont2", UsernameGenerator.resolveAvailable("jean.dupont", taken::contains));
    }

    @Test
    void resolveAvailable_multipleCollisions_incrementsSuffix() {
        Set<String> taken = new HashSet<>(Set.of(
                "jean.dupont", "jean.dupont2", "jean.dupont3", "jean.dupont4"));
        assertEquals("jean.dupont5", UsernameGenerator.resolveAvailable("jean.dupont", taken::contains));
    }

    @Test
    void resolveAvailable_reservedBase_startsAtSuffix2() {
        // 'admin' is reserved -> the base itself is skipped, candidate is "admin2".
        assertEquals("admin2", UsernameGenerator.resolveAvailable("admin", NEVER_TAKEN));
    }

    @Test
    void resolveAvailable_reservedBaseWithCollisions_skipsExistingSuffixes() {
        Set<String> taken = new HashSet<>(Set.of("admin2", "admin3"));
        assertEquals("admin4", UsernameGenerator.resolveAvailable("admin", taken::contains));
    }

    @Test
    void resolveAvailable_longBase_trimsBeforeSuffix() {
        // Base is 30 chars + suffix would overflow -> base must be trimmed.
        String longBase = "a".repeat(30);
        Set<String> taken = new HashSet<>(Set.of(longBase));
        String resolved = UsernameGenerator.resolveAvailable(longBase, taken::contains);

        assertEquals(30, resolved.length());
        assertTrue(resolved.endsWith("2"));
        // The trimmed base + "2" must still be valid.
        assertTrue(UsernameGenerator.isValid(resolved));
    }

    @Test
    void generate_combinesSlugifyAndResolveAvailable() {
        Set<String> taken = new HashSet<>(Set.of("jean.dupont"));
        String result = UsernameGenerator.generate("Jean Dupont", null, null, taken::contains);
        assertEquals("jean.dupont2", result);
    }

    @Test
    void generate_emptyInputs_userBaseWithCollisions() {
        Set<String> taken = new HashSet<>(Set.of("user", "user2"));
        String result = UsernameGenerator.generate(null, null, null, taken::contains);
        assertEquals("user3", result);
    }

    // ─── Latin-extended pre-translation (mirrors `unaccent`) ─────────────────
    // Sentinel coverage for every entry in LATIN_EXT_PRETRANSLATE. NFD alone
    // does NOT decompose these structural ligatures/strokes — drift would
    // silently break international usernames at signup time.

    @Test
    void slugify_latinExtendedLetters_translateToAscii() {
        assertEquals("dorde", UsernameGenerator.slugify("Đorđe", null, null));
        assertEquals("lukasz", UsernameGenerator.slugify("Łukasz", null, null));
        assertEquals("oystein", UsernameGenerator.slugify("Øystein", null, null));
        assertEquals("aether", UsernameGenerator.slugify("Æther", null, null));
        assertEquals("oeuvre", UsernameGenerator.slugify("Œuvre", null, null));
        assertEquals("strasse", UsernameGenerator.slugify("Straße", null, null));
        assertEquals("thor", UsernameGenerator.slugify("Þor", null, null));
        // Lowercase ð inside a longer string — translates to 'd' before NFD.
        assertEquals("hedge", UsernameGenerator.slugify("heðge", null, null));
    }

    @Test
    void resolveAvailable_baseTrimmingPreservesValidity_withMultiDigitSuffix() {
        // Force the suffix to grow to 2 digits while the base is already at
        // MAX_LENGTH — exercises buildCandidate's trimmed-base branch with a
        // wider suffix.
        String longBase = "a".repeat(30);
        Set<String> taken = new HashSet<>();
        for (int i = 1; i <= 12; i++) {
            taken.add(longBase.substring(0, 30 - String.valueOf(i).length()) + i);
        }
        taken.add(longBase);
        String resolved = UsernameGenerator.resolveAvailable(longBase, taken::contains);
        assertEquals(30, resolved.length());
        assertTrue(resolved.endsWith("13"));
        assertTrue(UsernameGenerator.isValid(resolved));
    }

    @Test
    void slugify_displayNameWithOnlyDroppedChars_fallsBackToFirstLast() {
        // The displayName "***!!!" collapses to "" after charset stripping —
        // pickFirstNonBlank returned "***!!!" (non-blank by trim semantics)
        // and yields a too-short slug, which triggers the second fallback to
        // firstName.lastName.
        // NB: pickFirstNonBlank only checks for "blank" (whitespace) so the
        // displayName is selected ; the subsequent length check then bumps
        // the result to the "user" fallback.
        assertEquals("user", UsernameGenerator.slugify("***!!!", null, null));
    }
}
