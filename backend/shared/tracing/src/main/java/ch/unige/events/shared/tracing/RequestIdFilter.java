package ch.unige.events.shared.tracing;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logmanager.MDC;

import java.util.UUID;

/**
 * Server-side filter that pins the {@code X-Request-ID} header to the
 * SLF4J / log-manager MDC under the key {@value #MDC_KEY}, and echoes
 * it back on the response. If the incoming request lacks the header
 * (rare — Kong's correlation-id plugin posts it on every external
 * request), a fresh UUID is generated.
 *
 * <p>Runs before authentication (priority {@code AUTHENTICATION - 1})
 * so the auth filter sees an MDC-decorated thread.
 */
@Provider
@Priority(Priorities.AUTHENTICATION - 1)
public class RequestIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String HEADER = "X-Request-ID";
    public static final String MDC_KEY = "requestId";

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String requestId = requestContext.getHeaderString(HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        requestContext.setProperty(MDC_KEY, requestId);
        MDC.put(MDC_KEY, requestId);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        Object requestId = requestContext.getProperty(MDC_KEY);
        if (requestId != null) {
            responseContext.getHeaders().add(HEADER, requestId);
        }
        MDC.remove(MDC_KEY);
    }
}
