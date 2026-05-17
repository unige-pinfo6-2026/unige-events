package ch.unige.events.user.service;

import java.text.Normalizer;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Pure helpers for the SCRUM-169 username system :
 *
 * <ul>
 *   <li>{@link #slugify(String, String, String)} — derives the base slug from
 *       {@code displayName} (preferred), {@code firstName.lastName} (fallback),
 *       or the literal {@code "user"}. Mirrors the PL/pgSQL block in
 *       {@code V3__add_user_username.sql} : ASCII fold via NFD, lowercase,
 *       whitespace to {@code '.'}, drop chars outside {@code [a-z0-9._-]},
 *       collapse runs of {@code '.'}, trim leading/trailing punctuation,
 *       truncate to 30 chars.</li>
 *   <li>{@link #isReserved(String)} — checks {@link #RESERVED} which mirrors
 *       the blocklist documented in {@code data-model.md} and rejected by
 *       both {@code UpdateUsernameRequest} validation and the V3 back-fill.</li>
 *   <li>{@link #resolveAvailable(String, Predicate)} — appends the smallest
 *       numeric suffix that yields an unused username. A reserved base name
 *       starts at suffix {@code 2} so the base itself is skipped.</li>
 *   <li>{@link #generate(String, String, String, Predicate)} — convenience
 *       combinator used at signup time.</li>
 *   <li>{@link #isValid(String)} — pattern check used by
 *       {@code UserService.updateUsername} to short-circuit validation
 *       errors with the canonical {@code username_invalid} envelope.</li>
 * </ul>
 *
 * <p>The PL/pgSQL migration cannot call this Java code — they are
 * intentional duplicates. Keeping the logic identical is a contract :
 * whenever this helper is touched, the migration comment must be updated
 * and the {@code UsernameGeneratorTest} sentinels re-run against both
 * paths (the latter via {@code UsernameMigrationBackfillTest}, deferred —
 * cf. spec § 6.3 risk note).
 */
public final class UsernameGenerator {

    /** Pattern enforced both at the DB CHECK level (V3) and by request validation. */
    public static final Pattern VALID_PATTERN = Pattern.compile("^[a-z0-9._-]{3,30}$");

    /** Maximum length — matches {@code VARCHAR(30)} from V3. */
    public static final int MAX_LENGTH = 30;

    /** Minimum length — keeps usernames distinguishable. */
    public static final int MIN_LENGTH = 3;

    /**
     * Blocklist mirrored by the V3 back-fill DO block + the frontend
     * {@code RESERVED_USERNAMES} const. Squat-proof against the route alias
     * {@code /profile/me} and obvious squats (admin, api, ...).
     */
    public static final Set<String> RESERVED = Set.of(
            "me", "admin", "api", "login", "logout", "signup", "register", "settings"
    );

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALLOWED = Pattern.compile("[^a-z0-9._-]");
    private static final Pattern DOTS = Pattern.compile("\\.+");
    private static final Pattern LEADING_PUNCT = Pattern.compile("^[._-]+");
    private static final Pattern TRAILING_PUNCT = Pattern.compile("[._-]+$");

    /**
     * Pre-translation for Latin-extended characters that NFD does NOT
     * decompose (they encode structural modifications, not diacritics).
     * Mirrors the PostgreSQL {@code unaccent} extension default rules used
     * by the V3 migration so Java auto-gen and SQL back-fill stay aligned
     * for international names (Eastern European Đ/Ł, Scandinavian Ø/Æ,
     * German ß).
     */
    private static final java.util.Map<Character, String> LATIN_EXT_PRETRANSLATE = java.util.Map.ofEntries(
            java.util.Map.entry('Đ', "D"), java.util.Map.entry('đ', "d"),
            java.util.Map.entry('Ł', "L"), java.util.Map.entry('ł', "l"),
            java.util.Map.entry('Ø', "O"), java.util.Map.entry('ø', "o"),
            java.util.Map.entry('Æ', "AE"), java.util.Map.entry('æ', "ae"),
            java.util.Map.entry('Œ', "OE"), java.util.Map.entry('œ', "oe"),
            java.util.Map.entry('ß', "ss"),
            java.util.Map.entry('Þ', "TH"), java.util.Map.entry('þ', "th"),
            java.util.Map.entry('Ð', "D"), java.util.Map.entry('ð', "d")
    );

    private UsernameGenerator() {
        // util class — no instances
    }

    /** Whether {@code candidate} matches the persisted/DB-CHECK pattern. */
    public static boolean isValid(String candidate) {
        return candidate != null && VALID_PATTERN.matcher(candidate).matches();
    }

    /** Whether {@code candidate} (lowered) appears in the squat blocklist. */
    public static boolean isReserved(String candidate) {
        return candidate != null && RESERVED.contains(candidate.toLowerCase());
    }

    /**
     * Compute the base slug for back-fill / auto-gen. Always returns a string
     * that satisfies the pattern (3..30 chars, valid charset) — fallback
     * {@code "user"} is the last resort when everything else is empty.
     *
     * <p>Caller is responsible for collision resolution via
     * {@link #resolveAvailable(String, Predicate)}.
     */
    public static String slugify(String displayName, String firstName, String lastName) {
        String source = pickFirstNonBlank(
                displayName,
                joinNonBlank(firstName, lastName, "."),
                "user"
        );

        // Pre-translate Latin-extended characters (Đ, Ł, Ø, ß, ...) that NFD
        // cannot decompose. Then NFD-fold the rest of the diacritics and
        // lowercase.
        String folded = preTranslateLatinExt(source);
        folded = Normalizer.normalize(folded, Normalizer.Form.NFD);
        folded = DIACRITICS.matcher(folded).replaceAll("");
        folded = folded.toLowerCase();

        // Normalise to allowed charset, collapse runs of '.', trim punctuation.
        folded = WHITESPACE.matcher(folded).replaceAll(".");
        folded = NON_ALLOWED.matcher(folded).replaceAll("");
        folded = DOTS.matcher(folded).replaceAll(".");
        folded = LEADING_PUNCT.matcher(folded).replaceAll("");
        folded = TRAILING_PUNCT.matcher(folded).replaceAll("");

        if (folded.length() > MAX_LENGTH) {
            folded = folded.substring(0, MAX_LENGTH);
            folded = TRAILING_PUNCT.matcher(folded).replaceAll("");
        }

        if (folded.length() < MIN_LENGTH) {
            folded = "user";
        }

        return folded;
    }

    /**
     * Resolve a unique username from {@code base} given a predicate that
     * tells whether a candidate is already taken. A reserved base name
     * starts at suffix {@code 2} so the base itself is skipped — this
     * matches the V3 back-fill semantics.
     *
     * <p>The candidate length is always kept within {@link #MAX_LENGTH} by
     * trimming the base before concatenating the suffix.
     */
    public static String resolveAvailable(String base, Predicate<String> isTaken) {
        int suffix = isReserved(base) ? 2 : 1;
        String candidate = (suffix == 1) ? base : buildCandidate(base, suffix);

        while (isTaken.test(candidate)) {
            suffix++;
            candidate = buildCandidate(base, suffix);
        }
        return candidate;
    }

    /**
     * One-shot helper used by {@code UserService.getOrCreateUser} : compute
     * the slug then resolve to an available username via {@code isTaken}.
     */
    public static String generate(
            String displayName,
            String firstName,
            String lastName,
            Predicate<String> isTaken
    ) {
        return resolveAvailable(slugify(displayName, firstName, lastName), isTaken);
    }

    private static String buildCandidate(String base, int suffix) {
        String suffixStr = Integer.toString(suffix);
        int budget = MAX_LENGTH - suffixStr.length();
        String trimmedBase = (base.length() <= budget) ? base : base.substring(0, budget);
        return trimmedBase + suffixStr;
    }

    private static String preTranslateLatinExt(String source) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        StringBuilder sb = new StringBuilder(source.length() + 8);
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            String mapped = LATIN_EXT_PRETRANSLATE.get(c);
            if (mapped != null) {
                sb.append(mapped);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String pickFirstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        if (second != null && !second.trim().isEmpty()) {
            return second.trim();
        }
        return fallback;
    }

    private static String joinNonBlank(String left, String right, String sep) {
        String l = (left == null) ? "" : left.trim();
        String r = (right == null) ? "" : right.trim();
        if (l.isEmpty() && r.isEmpty()) {
            return "";
        }
        if (l.isEmpty()) {
            return r;
        }
        if (r.isEmpty()) {
            return l;
        }
        return l + sep + r;
    }
}
