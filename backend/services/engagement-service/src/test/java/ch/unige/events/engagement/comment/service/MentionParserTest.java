package ch.unige.events.engagement.comment.service;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link MentionParser}. Annotated {@code @QuarkusTest} so the
 * executed parser bytecode (including the null/blank short-circuit) is
 * captured by quarkus-jacoco — plain unit tests do not contribute coverage.
 */
@QuarkusTest
class MentionParserTest {

    private final MentionParser parser = new MentionParser();

    @Test
    void nullContent_emptySet() {
        assertEquals(Set.of(), parser.extractHandles(null));
    }

    @Test
    void blankContent_emptySet() {
        assertEquals(Set.of(), parser.extractHandles("   \t\n"));
    }

    @Test
    void noMentions_emptySet() {
        assertEquals(Set.of(), parser.extractHandles("Just a normal comment"));
    }

    @Test
    void singleMention_atStart() {
        assertEquals(Set.of("alice.dosh"),
                parser.extractHandles("@alice.dosh hi"));
    }

    @Test
    void singleMention_inMiddle() {
        assertEquals(Set.of("alice.dosh"),
                parser.extractHandles("hi @alice.dosh, nice to see you"));
    }

    @Test
    void multipleMentions_preserveFirstAppearanceOrder() {
        assertEquals(List.of("alice.dosh", "bob.smith"),
                parser.extractHandles("@alice.dosh and @bob.smith").stream().toList());
    }

    @Test
    void duplicateMentions_dedupedToOne() {
        assertEquals(Set.of("alice.dosh"),
                parser.extractHandles("@alice.dosh @alice.dosh @alice.dosh"));
    }

    @Test
    void mixedCaseMentions_lowercasedInOutput() {
        assertEquals(Set.of("alice.dosh"),
                parser.extractHandles("@Alice.DOSH"));
    }

    @Test
    void handleTooShort_rejected() {
        // SCRUM-169 minimum length is 3 — anything shorter isn't a valid
        // username so we don't even try to resolve.
        assertEquals(Set.of(), parser.extractHandles("@ab @x"));
    }

    @Test
    void handleTooLong_dropped() {
        // The negative lookahead `(?![a-z0-9._-])` means a token longer
        // than 30 chars is rejected entirely — we don't truncate to a
        // partial prefix that could collide with another user's handle.
        String content = "@" + "a".repeat(35);
        assertEquals(Set.of(), parser.extractHandles(content));
    }

    @Test
    void emailLike_rejected() {
        // `foo@bar` must NOT capture `bar` — the negative lookbehind on
        // the `@` requires the previous char to be non-word. Same rule
        // as the frontend splitMentions.
        assertEquals(Set.of(), parser.extractHandles("send to foo@example.com"));
        assertEquals(Set.of(), parser.extractHandles("foo@bar.baz"));
    }

    @Test
    void mentionAfterPunctuation_stillCaptured() {
        // Lookbehind rejects \w chars only — punctuation, brackets, quotes
        // are all fine ("hi(@alice)" should still mention alice... if alice
        // satisfies length).
        assertEquals(Set.of("alice.dosh"),
                parser.extractHandles("hi (@alice.dosh)"));
    }

    @Test
    void trailingPunctuation_notIncluded() {
        // Negative lookahead trims `,` `!` `.` etc.
        assertEquals(Set.of("alice.dosh"),
                parser.extractHandles("@alice.dosh, thanks!"));
    }
}
