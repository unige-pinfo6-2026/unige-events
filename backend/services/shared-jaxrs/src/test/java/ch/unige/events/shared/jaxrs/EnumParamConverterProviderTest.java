package ch.unige.events.shared.jaxrs;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.ext.ParamConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnumParamConverterProviderTest {

    enum SampleStatus { ACTIVE, INACTIVE }

    private final EnumParamConverterProvider provider = new EnumParamConverterProvider();

    @SuppressWarnings("unchecked")
    private ParamConverter<SampleStatus> sampleConverter() {
        ParamConverter<?> raw = provider.getConverter(SampleStatus.class, SampleStatus.class, new java.lang.annotation.Annotation[0]);
        assertNotNull(raw);
        return (ParamConverter<SampleStatus>) raw;
    }

    @Test
    void getConverter_nonEnum_returnsNull() {
        assertNull(provider.getConverter(String.class, String.class, new java.lang.annotation.Annotation[0]));
    }

    @Test
    void getConverter_timeframe_skipsToDefer() {
        // Timeframe stays under TimeframeParamConverterProvider authority.
        assertNull(provider.getConverter(Timeframe.class, Timeframe.class, new java.lang.annotation.Annotation[0]));
    }

    @Test
    void fromString_caseInsensitive_works() {
        assertEquals(SampleStatus.ACTIVE, sampleConverter().fromString("active"));
        assertEquals(SampleStatus.INACTIVE, sampleConverter().fromString("INACTIVE"));
    }

    @Test
    void fromString_nullOrBlank_returnsNull() {
        assertNull(sampleConverter().fromString(null));
        assertNull(sampleConverter().fromString(""));
        assertNull(sampleConverter().fromString("  "));
    }

    @Test
    void fromString_invalidEnum_throwsBadRequest() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> sampleConverter().fromString("BOGUS"));
        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    void toString_roundtripsName() {
        assertEquals("ACTIVE", sampleConverter().toString(SampleStatus.ACTIVE));
        assertNull(sampleConverter().toString(null));
    }
}
