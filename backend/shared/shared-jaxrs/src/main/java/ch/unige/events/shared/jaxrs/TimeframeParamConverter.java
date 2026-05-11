package ch.unige.events.shared.jaxrs;

import jakarta.ws.rs.ext.ParamConverter;

/**
 * Converts between {@link Timeframe} enum values and case-insensitive
 * String forms used in query parameters. Returns {@code null} for null
 * or blank input — the JAX-RS layer treats absent params as null, which
 * lets resources keep an "all" semantic when the param is omitted.
 */
public class TimeframeParamConverter implements ParamConverter<Timeframe> {

    @Override
    public Timeframe fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Timeframe.valueOf(value.toUpperCase());
    }

    @Override
    public String toString(Timeframe value) {
        return value == null ? null : value.name();
    }
}
