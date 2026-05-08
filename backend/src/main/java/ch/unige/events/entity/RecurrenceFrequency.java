package ch.unige.events.entity;

/**
 * Fréquence de récurrence d'un événement (SCRUM-147).
 * <p>
 * Sérialisé en String dans le JSON via Jackson et conservé tel quel dans
 * {@link Event#recurrenceRule} (format RFC 5545 simplifié).
 */
public enum RecurrenceFrequency {
    /** Occurrences espacées de 7 jours ({@code Period.ofDays(7)}). */
    WEEKLY,

    /** Occurrences espacées de 14 jours ({@code Period.ofDays(14)}). */
    BIWEEKLY,

    /** Occurrences espacées de 1 mois calendaire ({@code Period.ofMonths(1)}). */
    MONTHLY
}
