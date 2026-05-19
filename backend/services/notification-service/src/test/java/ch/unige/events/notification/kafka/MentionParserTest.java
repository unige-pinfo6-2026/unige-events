package ch.unige.events.notification.kafka;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for {@link MentionParser}. {@code @QuarkusTest} is not
 * needed — the parser has no CDI dependencies — but the production class
 * stays {@code @ApplicationScoped} for injection in the consumer tests
 * (no static-method-on-utility footgun).
 */
class MentionParserTest {

    private final MentionParser parser = new MentionParser();

    @Test
    void empty_returnsEmptySet() {
        assertTrue(parser.extractHandles("").isEmpty());
    }

    @Test
    void null_returnsEmptySet() {
        assertTrue(parser.extractHandles(null).isEmpty());
    }

    @Test
    void blank_returnsEmptySet() {
        assertTrue(parser.extractHandles("   \t\n  ").isEmpty());
    }

    @Test
    void noMentions_returnsEmptySet() {
        assertTrue(parser.extractHandles("Hello world, no @ here that matches").isEmpty());
    }

    @Test
    void singleMention_simple() {
        assertEquals(Set.of("alice.dosh"), parser.extractHandles("Hello @alice.dosh"));
    }

    @Test
    void multipleMentions_dedupCollapsesRepeats() {
        // "@bob" is exactly 3 chars — it matches. The two identical
        // "@alice.dosh" collapse to a single entry via the Set.
        assertEquals(Set.of("alice.dosh", "bob"),
                parser.extractHandles("@alice.dosh @bob @alice.dosh"));
    }

    @Test
    void multipleMentions_distinct_preserveOrder() {
        // LinkedHashSet keeps order — assert the iteration order matches
        // the textual order of first appearance.
        Set<String> out = parser.extractHandles("@bob.smith then @alice.dosh");
        assertEquals(List.of("bob.smith", "alice.dosh"), out.stream().toList());
    }

    @Test
    void caseInsensitive_lowercased() {
        // The regex tolerates uppercase chars but the result is lowered
        // so the DB lookup matches the persisted (always-lowercase) row.
        assertEquals(Set.of("alice.dosh"),
                parser.extractHandles("@Alice.Dosh and @ALICE.DOSH"));
    }

    @Test
    void punctuationTrailing_ok() {
        // The negative lookahead drops the trailing punctuation from the
        // capture (comma, exclamation, period at end of sentence, etc.).
        assertEquals(Set.of("alice.dosh"), parser.extractHandles("@alice.dosh, thanks!"));
    }

    @Test
    void emailLike_capturesDomainPart() {
        // Acknowledged false positive (Décision E) — `email@example.com`
        // captures `example.com` as a candidate handle. The consumer
        // resolves it against user-service ; no row matches ; silent skip.
        // No notification is produced.
        assertEquals(Set.of("example.com"), parser.extractHandles("contact@example.com for help"));
    }

    @Test
    void consecutiveAts_capturesSecond() {
        // "@@alice.dosh" — the first @ is wasted on the literal, the second
        // begins the actual match.
        assertEquals(Set.of("alice.dosh"), parser.extractHandles("@@alice.dosh"));
    }

    @Test
    void minLength_3_enforced() {
        assertEquals(Set.of("abc"), parser.extractHandles("@ab @abc"));
    }

    @Test
    void maxLength_30_capturedWhenTerminatedByBoundary() {
        // 30 chars then a non-charset boundary — captures cleanly at 30.
        String thirty = "a".repeat(30);
        assertEquals(Set.of(thirty), parser.extractHandles("@" + thirty + " end"));
    }

    @Test
    void maxLength_31_rejectedByLookahead() {
        // 31 chars all in-charset — the negative lookahead refuses the
        // 30-char prefix (the 31st char would still be in [a-z0-9._-]),
        // and every shorter prefix runs into the same problem. The whole
        // string is silently dropped — a too-long handle is not a valid
        // truncation of one. The consumer never even hits user-service.
        String thirtyOne = "a".repeat(31);
        assertTrue(parser.extractHandles("@" + thirtyOne).isEmpty());
    }

    @Test
    void noBoundaryNeeded_atEndOfString() {
        assertEquals(Set.of("alice.dosh"), parser.extractHandles("@alice.dosh"));
    }

    @Test
    void accentedTextDoesNotCreateMention() {
        // Charset is ASCII — François' name does not generate a handle.
        // The actual @alice.dosh mention is still captured.
        assertEquals(Set.of("alice.dosh"),
                parser.extractHandles("François a dit @alice.dosh"));
    }

    @Test
    void mentionInsideSentenceWithMultipleSpaces() {
        assertEquals(Set.of("alice.dosh", "bob.smith"),
                parser.extractHandles("  @alice.dosh    saw   @bob.smith   yesterday  "));
    }

    @Test
    void handleWithDashAndDot_captured() {
        // The charset includes `-` and `.` per SCRUM-169 — make sure both
        // are accepted inside a handle (not just at boundaries).
        assertEquals(Set.of("ann-marie.dosh"),
                parser.extractHandles("hey @ann-marie.dosh"));
    }

    @Test
    void manyMentions_allCaptured() {
        // Sanity check on volume — a comment crammed with mentions should
        // round-trip every distinct handle.
        Set<String> out = parser.extractHandles(
                "@user.one @user.two @user.three @user.four @user.five");
        assertEquals(5, out.size());
    }
}
