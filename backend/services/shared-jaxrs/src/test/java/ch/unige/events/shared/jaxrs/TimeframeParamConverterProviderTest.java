package ch.unige.events.shared.jaxrs;

import jakarta.ws.rs.ext.ParamConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class TimeframeParamConverterProviderTest {

    private final TimeframeParamConverterProvider provider = new TimeframeParamConverterProvider();

    @Test
    void timeframeClass_returnsTimeframeConverter() {
        ParamConverter<Timeframe> c = provider.getConverter(Timeframe.class, null, null);
        assertInstanceOf(TimeframeParamConverter.class, c);
    }

    @Test
    void otherClass_returnsNull() {
        assertNull(provider.getConverter(String.class, null, null));
        assertNull(provider.getConverter(Integer.class, null, null));
    }
}
