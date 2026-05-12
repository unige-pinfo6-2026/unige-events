package ch.unige.events.shared.jaxrs;

/**
 * Time-window filter used by {@code /me/attendances} and similar
 * endpoints to filter events by start date relative to {@code now}.
 */
public enum Timeframe {
    UPCOMING,
    PAST
}
