package ch.unige.events.shared.jaxrs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimeframeParamConverterTest {

    private final TimeframeParamConverter converter = new TimeframeParamConverter();

    @Test
    void fromString_upper_returnsEnum() {
        assertEquals(Timeframe.UPCOMING, converter.fromString("UPCOMING"));
        assertEquals(Timeframe.PAST, converter.fromString("PAST"));
    }

    @Test
    void fromString_lower_returnsEnum() {
        assertEquals(Timeframe.UPCOMING, converter.fromString("upcoming"));
        assertEquals(Timeframe.PAST, converter.fromString("past"));
    }

    @Test
    void fromString_mixedCase_returnsEnum() {
        assertEquals(Timeframe.UPCOMING, converter.fromString("Upcoming"));
    }

    @Test
    void fromString_null_returnsNull() {
        assertNull(converter.fromString(null));
    }

    @Test
    void fromString_blank_returnsNull() {
        assertNull(converter.fromString(""));
        assertNull(converter.fromString("   "));
    }

    @Test
    void fromString_invalid_throws() {
        assertThrows(IllegalArgumentException.class, () -> converter.fromString("nonsense"));
    }

    @Test
    void toString_value_returnsName() {
        assertEquals("UPCOMING", converter.toString(Timeframe.UPCOMING));
        assertEquals("PAST", converter.toString(Timeframe.PAST));
    }

    @Test
    void toString_null_returnsNull() {
        assertNull(converter.toString(null));
    }

    @Test
    void timeframeEnumValuesPinned() {
        // Sentinel against drift
        assertEquals(2, Timeframe.values().length);
        assertEquals(Timeframe.UPCOMING, Timeframe.values()[0]);
        assertEquals(Timeframe.PAST, Timeframe.values()[1]);
    }
}
