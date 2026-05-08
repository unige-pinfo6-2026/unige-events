package ch.unige.events.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stamps every request/response with a {@code X-Request-ID} correlation ID.
 *
 * <p>Pentest 2026-04-17 finding 4.7 — without a correlation ID, when a user
 * reports suspicious activity or a bug there is no client-side handle to find
 * the request in the logs. This filter:
 * <ul>
 *   <li>Honours a client-supplied {@code X-Request-ID} if it matches a
 *       narrowly-scoped pattern (hex digits and dashes, 8–64 chars). Anything
 *       else is silently replaced with a freshly-generated UUID — never a 400,
 *       since this is a diagnostic header and rejecting requests on it would
 *       be a footgun.</li>
 *   <li>Pushes the resolved id onto the SLF4J MDC under the key
 *       {@code requestId}, so the log pattern in {@code application.properties}
 *       (which references {@code %X{requestId}}) can render it on every line
 *       emitted while the request is in flight.</li>
 *   <li>Echoes the id back as a response header so the client can quote it in
 *       support tickets.</li>
 * </ul>
 *
 * <p>Lightweight stand-in for full distributed tracing (OpenTelemetry) — out
 * of scope for this ticket.
 */
@Provider
@ApplicationScoped
public class RequestIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    static final String HEADER = "X-Request-ID";
    static final String PROPERTY = "requestId";
    static final String MDC_KEY = "requestId";

    /**
     * Hex characters and dashes, 8–64 chars. Tight enough to keep injection
     * surface negligible (no whitespace, no control chars, no unicode), loose
     * enough to accept UUIDs, ULIDs, and short request-scoped tokens.
     */
    private static final Pattern ID_PATTERN = Pattern.compile("^[0-9a-fA-F-]{8,64}$");

    @Override
    public void filter(ContainerRequestContext req) {
        String supplied = req.getHeaderString(HEADER);
        String id = (supplied != null && ID_PATTERN.matcher(supplied).matches())
                ? supplied
                : UUID.randomUUID().toString();
        req.setProperty(PROPERTY, id);
        MDC.put(MDC_KEY, id);
    }

    @Override
    public void filter(ContainerRequestContext req, ContainerResponseContext res) {
        String id = (String) req.getProperty(PROPERTY);
        if (id != null) {
            res.getHeaders().add(HEADER, id);
        }
        // Always remove — even on error paths — so the MDC doesn't leak across
        // pooled request threads.
        MDC.remove(MDC_KEY);
    }
}
