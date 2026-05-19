package ch.unige.events.notification.kafka;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts {@code @<handle>} mentions from a comment body — SCRUM-145.
 *
 * <p>Regex shape (Décision E) :
 * <pre>{@code @([a-z0-9._-]{3,30})(?![a-z0-9._-])}</pre>
 *
 * <ul>
 *   <li>Charset {@code [a-z0-9._-]} mirrors the SCRUM-169 username regex
 *       ({@code ^[a-z0-9._-]{3,30}$}) — anything outside cannot ever
 *       resolve to a user, so capturing it would waste a hop.</li>
 *   <li>{@code {3,30}} matches the column length and the {@code @NotBlank}
 *       / {@code @Pattern} contract on {@code UpdateUsernameRequest}.</li>
 *   <li>Negative lookahead {@code (?![a-z0-9._-])} keeps the trailing
 *       comma / exclamation mark out of the capture
 *       ({@code "@alice.dosh, thanks"} captures {@code alice.dosh}).</li>
 *   <li>{@link Pattern#CASE_INSENSITIVE} tolerates {@code @Alice.Dosh} —
 *       the result is lowered before insertion in the Set so the DB
 *       lookup matches the persisted lowercase row.</li>
 * </ul>
 *
 * <p>Returns a {@link LinkedHashSet} so the iteration order is the order
 * of first appearance — useful for log lines and deterministic test
 * assertions. Duplicates collapse mechanically
 * ({@code "@x @x @x"} → {@code [x]}). Self-mention filtering and
 * resolution of unknown handles are the <strong>consumer's</strong>
 * responsibility — the parser is pure string-in / set-out.
 *
 * <p>Lives in {@code notification-service.kafka} (not in a shared lib)
 * per Décision F : nobody else parses mentions today, and the future
 * frontend autocomplete will mirror the regex in TypeScript anyway. Move
 * to a shared module on rule-of-three.
 */
@ApplicationScoped
public class MentionParser {

    private static final Pattern MENTION_RE = Pattern.compile(
            "@([a-z0-9._-]{3,30})(?![a-z0-9._-])",
            Pattern.CASE_INSENSITIVE);

    /**
     * Returns the lowercased, deduplicated, order-preserving set of
     * {@code @<handle>} mentions found in {@code content}. Empty,
     * blank, or null input yields {@link Set#of()} — no exception.
     */
    public Set<String> extractHandles(String content) {
        if (content == null || content.isBlank()) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        Matcher m = MENTION_RE.matcher(content);
        while (m.find()) {
            // Locale.ROOT — handles are ASCII [a-z0-9._-], but a default
            // locale (e.g. Turkish) could still mangle a capture in pathological
            // cases. ROOT is the safe default for identifier-style strings.
            out.add(m.group(1).toLowerCase(Locale.ROOT));
        }
        return out;
    }
}
