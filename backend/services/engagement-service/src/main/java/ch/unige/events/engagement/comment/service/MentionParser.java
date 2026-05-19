package ch.unige.events.engagement.comment.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts {@code @<handle>} mentions from a comment body. Moved into
 * engagement-service so the producer can resolve handles → UUIDs
 * <strong>before</strong> the Kafka boundary, mirroring the follow-lifecycle
 * pattern (the producer decides who needs to be notified ; the consumer is
 * trivial).
 *
 * <p>Regex shape :
 * <pre>{@code @([a-z0-9._-]{3,30})(?![a-z0-9._-])}</pre>
 *
 * <ul>
 *   <li>Charset mirrors the SCRUM-169 username regex.</li>
 *   <li>{@code {3,30}} matches the column length + bean-validation contract.</li>
 *   <li>Negative lookahead trims punctuation
 *       ({@code "@alice.dosh, thanks"} → {@code alice.dosh}).</li>
 *   <li>{@link Pattern#CASE_INSENSITIVE} tolerates {@code @Alice.Dosh} —
 *       lowercased before going into the set so the DB lookup matches the
 *       persisted lowercase row.</li>
 * </ul>
 *
 * <p>Returns a {@link LinkedHashSet} so iteration order is the order of
 * first appearance — deterministic test assertions, predictable log output.
 * Duplicates collapse ({@code "@x @x"} → {@code [x]}).
 */
@ApplicationScoped
public class MentionParser {

    private static final Pattern MENTION_RE = Pattern.compile(
            "@([a-z0-9._-]{3,30})(?![a-z0-9._-])",
            Pattern.CASE_INSENSITIVE);

    public Set<String> extractHandles(String content) {
        if (content == null || content.isBlank()) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        Matcher m = MENTION_RE.matcher(content);
        while (m.find()) {
            out.add(m.group(1).toLowerCase(Locale.ROOT));
        }
        return out;
    }
}
