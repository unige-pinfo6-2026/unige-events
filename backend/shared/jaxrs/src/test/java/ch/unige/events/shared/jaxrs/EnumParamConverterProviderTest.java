package ch.unige.events.shared.jaxrs;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.ext.ParamConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void getConverter_nullRawType_returnsNull() {
        assertNull(provider.getConverter(null, null, new java.lang.annotation.Annotation[0]));
    }

    // Étape 24.8.3 (E3): Timeframe used to be skipped via a hard-coded
    // rawType == Timeframe.class branch. The skip is now handled by
    // JAX-RS @Priority dispatch — TimeframeParamConverterProvider runs
    // first and wins. At the unit-provider level (called directly,
    // outside JAX-RS), the generic provider does return a converter
    // for Timeframe, which is fine because no production code path
    // invokes EnumParamConverterProvider directly.
    @Test
    void getConverter_timeframe_returnsGenericConverter_butLowerPriorityThanSpecific() {
        ParamConverter<?> generic = provider.getConverter(
                Timeframe.class, Timeframe.class, new java.lang.annotation.Annotation[0]);
        assertNotNull(generic, "Étape 24.8.3: Timeframe is no longer hard-coded as a skip");

        Priority enumPriority = EnumParamConverterProvider.class.getAnnotation(Priority.class);
        assertNotNull(enumPriority, "EnumParamConverterProvider must carry @Priority");
        Priority timeframePriority = TimeframeParamConverterProvider.class.getAnnotation(Priority.class);
        int timeframeValue = timeframePriority == null ? Priorities.USER : timeframePriority.value();
        assertTrue(enumPriority.value() > timeframeValue,
                "EnumParamConverterProvider must run AFTER TimeframeParamConverterProvider"
                        + " (lower priority number = runs first); got generic="
                        + enumPriority.value() + " specific=" + timeframeValue);
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
