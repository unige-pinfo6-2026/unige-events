package ch.unige.events.engagement.comment.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for {@link MentionParser}. {@code @QuarkusTest} is not
 * needed — the parser has no CDI / persistence dependencies, just a
 * static {@link java.util.regex.Pattern}.
 */
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
    void handleTooLong_truncatedToValidPrefix() {
        // {3,30} caps the capture at 30 chars ; the rest is left as text.
        // The trailing 'aa' after position 30 is not part of the handle.
        String content = "@" + "a".repeat(35);
        Set<String> handles = parser.extractHandles(content);
        assertEquals(1, handles.size());
        assertTrue(handles.iterator().next().length() == 30,
                "handle should be capped at 30 chars");
    }

    @Test
    void emailLike_rejected() {
        // No mention should be captured when the @ is preceded by a word
        // char — covers the typical `foo@example.com` case via the
        // negative-lookbehind effect of the surrounding char rules.
        // (The regex itself uses a lookahead on the trailing side ;
        // see commentary in MentionParser.)
        assertTrue(parser.extractHandles("send to foo@bar").stream()
                .noneMatch(h -> h.equals("bar")),
                "email-like token should not be captured");
    }

    @Test
    void trailingPunctuation_notIncluded() {
        // Negative lookahead trims `,` `!` `.` etc.
        assertEquals(Set.of("alice.dosh"),
                parser.extractHandles("@alice.dosh, thanks!"));
    }
}
