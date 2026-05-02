package ch.unige.events.util;

import java.text.Normalizer;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Generates a unique, URL-friendly public username from user-provided fields.
 *
 * Strategy (Sprint 7) :
 *   1. Slugify the displayName.
 *   2. Fallback to firstName.lastName.
 *   3. Fallback to "user".
 *   Anti-collision : append an incremental numeric suffix until a free slot is found,
 *   skipping the reserved blocklist.
 */
public final class UsernameGenerator {

    public static final Set<String> BLOCKLIST = Set.of(
            "me", "admin", "api", "login", "logout", "signup", "register", "settings"
    );

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 30;
    public static final String FALLBACK = "user";

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern INVALID_CHARS = Pattern.compile("[^a-z0-9._-]");
    private static final Pattern REPEATED_DOTS = Pattern.compile("\\.{2,}");
    private static final Pattern TRIM_SEPARATORS = Pattern.compile("^[._-]+|[._-]+$");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private UsernameGenerator() {}

    public static boolean isReserved(String candidate) {
        return candidate != null && BLOCKLIST.contains(candidate.toLowerCase());
    }

    /**
     * Returns a slugified candidate (lowercase, ASCII-folded, dots-as-separators)
     * or null if the input cannot produce a slug long enough.
     */
    public static String slug(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return null;

        String folded = Normalizer.normalize(trimmed, Normalizer.Form.NFD);
        folded = DIACRITICS.matcher(folded).replaceAll("");
        folded = folded.toLowerCase();
        folded = WHITESPACE.matcher(folded).replaceAll(".");
        folded = INVALID_CHARS.matcher(folded).replaceAll("");
        folded = REPEATED_DOTS.matcher(folded).replaceAll(".");
        folded = TRIM_SEPARATORS.matcher(folded).replaceAll("");

        if (folded.length() < MIN_LENGTH) return null;
        if (folded.length() > MAX_LENGTH) {
            folded = folded.substring(0, MAX_LENGTH);
            folded = TRIM_SEPARATORS.matcher(folded).replaceAll("");
        }
        return folded.length() < MIN_LENGTH ? null : folded;
    }

    /**
     * Picks a base slug from the available naming fields, falling back to "user".
     */
    public static String baseSlug(String displayName, String firstName, String lastName) {
        String fromDisplay = slug(displayName);
        if (fromDisplay != null) return fromDisplay;

        String composed = joinDotted(firstName, lastName);
        String fromComposed = slug(composed);
        if (fromComposed != null) return fromComposed;

        return FALLBACK;
    }

    /**
     * Returns the first candidate (base, base2, base3, …) that is not blocklisted
     * and that the {@code isTaken} predicate reports as free.
     */
    public static String firstAvailable(String base, Predicate<String> isTaken) {
        String trimmedBase = trimToFit(base, MAX_LENGTH);
        if (trimmedBase.isEmpty()) trimmedBase = FALLBACK;

        if (!isReserved(trimmedBase) && !isTaken.test(trimmedBase)) {
            return trimmedBase;
        }
        int suffix = 2;
        while (true) {
            String suffixStr = Integer.toString(suffix);
            String candidate = trimToFit(trimmedBase, MAX_LENGTH - suffixStr.length()) + suffixStr;
            if (!isReserved(candidate) && !isTaken.test(candidate)) {
                return candidate;
            }
            suffix++;
        }
    }

    private static String joinDotted(String first, String last) {
        boolean hasFirst = first != null && !first.trim().isEmpty();
        boolean hasLast = last != null && !last.trim().isEmpty();
        if (!hasFirst && !hasLast) return null;
        if (!hasFirst) return last;
        if (!hasLast) return first;
        return first + "." + last;
    }

    private static String trimToFit(String value, int max) {
        if (value == null) return "";
        if (max <= 0) return "";
        String result = value.length() > max ? value.substring(0, max) : value;
        return TRIM_SEPARATORS.matcher(result).replaceAll("");
    }
}
