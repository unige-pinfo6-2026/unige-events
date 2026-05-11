package ch.unige.events.shared.jaxrs;

import jakarta.ws.rs.NameBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a JAX-RS resource (or method) as service-to-service internal.
 * Used as a {@link NameBinding} hint by {@link InternalTokenFilter} to
 * gate the request on the {@code X-Internal-Token} header. Endpoints
 * carrying {@link Internal} are <strong>not</strong> exposed via Kong
 * (no public route) and are <strong>not</strong> part of
 * {@code openapi.yaml}; cf. {@code backend/docs/internal-endpoints.md}.
 *
 * <p>Décision C finalization-complete (SEC-002-bis BLOQUANT): defense
 * in depth on top of the K8s perimeter. Even if a pod inside the
 * cluster is compromised, the caller still has to know the secret
 * loaded from {@code unige.internal-token} (Doppler in prod).
 */
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Internal {
}
