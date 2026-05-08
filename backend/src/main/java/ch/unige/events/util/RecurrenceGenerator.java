package ch.unige.events.util;

import ch.unige.events.entity.RecurrenceFrequency;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

/**
 * Génère les couples (startDate, endDate) des occurrences d'une récurrence
 * (SCRUM-147). Fonction pure — testable hors Quarkus en pur JUnit.
 * <p>
 * La méthode retourne les occurrences <strong>hors parent</strong> (le parent vit
 * à {@code parentStart/parentEnd} et est créé séparément par le service). Le total
 * matérialisé est donc parent + N rows = 1 + N rows, plafonné à 52 (la limite
 * produit) — cette classe peut donc retourner au maximum 51 ranges.
 */
public final class RecurrenceGenerator {

    /** Limite hard d'occurrences générées (parent inclus). Cf. spec décision 9. */
    public static final int MAX_TOTAL_OCCURRENCES = 52;

    private RecurrenceGenerator() {}

    public record DateRange(LocalDateTime start, LocalDateTime end) {}

    /**
     * Génère les ranges (start, end) des occurrences hors parent.
     *
     * @param parentStart    date/heure de début du parent
     * @param parentEnd      date/heure de fin du parent
     * @param frequency      WEEKLY / BIWEEKLY / MONTHLY
     * @param untilDate      date inclusive jusqu'à laquelle générer ; {@code null} = pas de borne haute
     * @param maxOccurrences nombre max total d'occurrences (parent + enfants) ;
     *                       {@code null} = pas de borne explicite (plafonné à
     *                       {@link #MAX_TOTAL_OCCURRENCES} de toute façon).
     * @return liste de ranges hors parent (taille 0..51)
     * @throws IllegalArgumentException si {@code untilDate == null && maxOccurrences == null}
     */
    public static List<DateRange> generate(
            LocalDateTime parentStart,
            LocalDateTime parentEnd,
            RecurrenceFrequency frequency,
            LocalDate untilDate,
            Integer maxOccurrences
    ) {
        if (untilDate == null && maxOccurrences == null) {
            throw new IllegalArgumentException(
                    "RecurrenceGenerator requires at least one of untilDate or maxOccurrences");
        }

        Period spacing = switch (frequency) {
            case WEEKLY -> Period.ofDays(7);
            case BIWEEKLY -> Period.ofDays(14);
            case MONTHLY -> Period.ofMonths(1);
        };

        int effectiveCap = MAX_TOTAL_OCCURRENCES;
        if (maxOccurrences != null) {
            effectiveCap = Math.min(effectiveCap, maxOccurrences);
        }

        List<DateRange> ranges = new ArrayList<>();
        // n=1 = première occurrence APRÈS le parent. Le parent compte pour 1 dans le cap.
        for (int n = 1; n < effectiveCap; n++) {
            LocalDateTime start = parentStart.plus(spacing.multipliedBy(n));
            LocalDateTime end = parentEnd.plus(spacing.multipliedBy(n));

            if (untilDate != null && start.toLocalDate().isAfter(untilDate)) {
                break;
            }

            ranges.add(new DateRange(start, end));
        }
        return ranges;
    }
}
