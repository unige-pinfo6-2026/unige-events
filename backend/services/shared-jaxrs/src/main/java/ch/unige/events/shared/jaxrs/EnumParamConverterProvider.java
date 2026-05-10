package ch.unige.events.shared.jaxrs;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * Generic JAX-RS {@link ParamConverterProvider} that case-insensitively
 * binds every {@code @QueryParam}/{@code @PathParam} of an {@code enum}
 * type. Returns {@code null} for null/blank input (consistent with
 * {@link TimeframeParamConverterProvider} — absent params keep their
 * "all" semantic) and throws {@link jakarta.ws.rs.BadRequestException}
 * (HTTP 400) on an unknown value, replacing the JAX-RS default 404 that
 * obscures invalid enum payloads (cf. MINOR-004 / first-audit BUG-011).
 *
 * <p>Étape 5.3 finalization-complete — covers EventStatus,
 * AttendanceStatus, EventCategory, ReportStatus, ReportReason,
 * Faculty, RecurrenceFrequency, FollowStatus, CoOrganizerStatus and
 * any future enum without per-type boilerplate. Discovered via Jandex,
 * no application.properties registration required.
 *
 * <p>Skips {@link Timeframe} so the existing
 * {@link TimeframeParamConverterProvider} remains authoritative for
 * that type (preserves backward compatibility on its corner cases).
 */
@Provider
public class EnumParamConverterProvider implements ParamConverterProvider {

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
        if (rawType == null || !rawType.isEnum() || rawType == Timeframe.class) {
            return null;
        }
        Class<? extends Enum> enumType = (Class<? extends Enum>) rawType;
        return (ParamConverter<T>) new EnumParamConverter<>(enumType);
    }

    static final class EnumParamConverter<E extends Enum<E>> implements ParamConverter<E> {

        private final Class<E> enumType;

        EnumParamConverter(Class<E> enumType) {
            this.enumType = enumType;
        }

        @Override
        public E fromString(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return Enum.valueOf(enumType, value.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new WebApplicationException(
                        "Invalid value for " + enumType.getSimpleName() + ": " + value,
                        Response.Status.BAD_REQUEST);
            }
        }

        @Override
        public String toString(E value) {
            return value == null ? null : value.name();
        }
    }
}
