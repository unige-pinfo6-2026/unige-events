package ch.unige.events.shared.jaxrs;

import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * JAX-RS {@link Provider} that wires {@link TimeframeParamConverter}
 * into the resource scanning so any {@code @QueryParam("timeframe")
 * Timeframe} parameter is auto-bound. Discovered via Jandex by every
 * consuming Quarkus app — no application.properties registration
 * required.
 */
@Provider
public class TimeframeParamConverterProvider implements ParamConverterProvider {

    @Override
    @SuppressWarnings("unchecked")
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
        if (rawType == Timeframe.class) {
            return (ParamConverter<T>) new TimeframeParamConverter();
        }
        return null;
    }
}
