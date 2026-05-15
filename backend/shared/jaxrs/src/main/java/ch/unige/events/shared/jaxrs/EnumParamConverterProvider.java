package ch.unige.events.shared.jaxrs;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
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
 * <p>Étape 24.8.3 (E3): the previous version hard-coded a
 * {@code rawType == Timeframe.class} skip so the more specific
 * {@link TimeframeParamConverterProvider} stayed authoritative. That
 * coupling is replaced by {@link Priority} — this generic provider runs
 * <em>after</em> the specific Timeframe provider, so JAX-RS picks the
 * specific converter for {@link Timeframe} and falls back here for any
 * other enum without the generic provider needing to know about
 * Timeframe at all.
 */
@Provider
@Priority(Priorities.USER + 100)
public class EnumParamConverterProvider implements ParamConverterProvider {

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
        if (rawType == null || !rawType.isEnum()) {
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
