package ch.unige.events.entity;

/**
 * Query-only enum used to filter event lists by their position in time relative
 * to {@code now()}. Not persisted — purely a request parameter shape.
 *
 * <ul>
 *   <li>{@link #UPCOMING} : event end date is &gt;= now (an event currently
 *       happening is upcoming).</li>
 *   <li>{@link #PAST} : event end date is &lt; now.</li>
 * </ul>
 */
public enum Timeframe {
    UPCOMING,
    PAST
}
