package ch.unige.events.shared.tracing;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logmanager.MDC;

/**
 * REST-client filter that propagates the current MDC
 * {@value RequestIdFilter#MDC_KEY} to the outgoing
 * {@value RequestIdFilter#HEADER} header so cross-service calls carry
 * the same correlation id end-to-end.
 *
 * <p>Register on each {@code @RegisterRestClient} interface via
 * {@code @RegisterProvider(RequestIdClientFilter.class)} or globally
 * via Quarkus' rest-client property.
 */
@Provider
public class RequestIdClientFilter implements ClientRequestFilter {

    @Override
    public void filter(ClientRequestContext clientRequestContext) {
        Object requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId != null) {
            clientRequestContext.getHeaders().add(RequestIdFilter.HEADER, requestId.toString());
        }
    }
}
