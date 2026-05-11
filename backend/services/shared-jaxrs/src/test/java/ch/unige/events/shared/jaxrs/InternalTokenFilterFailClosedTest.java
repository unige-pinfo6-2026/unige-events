package ch.unige.events.shared.jaxrs;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the fail-closed integration of {@link InternalTokenFilter} with
 * MicroProfile {@code ConfigProvider} (Étape 24.7.3, C6 — pr-test-analyzer
 * I-3). Distinct from {@link InternalTokenFilterTest}, which uses the
 * package-private {@code expectedOverride} short-circuit: this suite
 * exercises the actual {@code ConfigProvider} resolution path with a
 * deliberately empty {@code unige.internal-token} value, asserting the
 * filter still rejects every request — the production guarantee that a
 * misconfigured (or never-configured) deployment cannot leak its
 * {@code @Internal} endpoints to the public.
 */
class InternalTokenFilterFailClosedTest {

    private Config emptyConfig;

    @BeforeEach
    void registerEmptyTokenConfig() {
        ConfigProviderResolver resolver = ConfigProviderResolver.instance();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            resolver.releaseConfig(resolver.getConfig(cl));
        } catch (IllegalStateException ignored) {
            // No existing config — first registration on this classloader.
        }
        emptyConfig = new SmallRyeConfigBuilder()
                .withSources(new PropertiesConfigSource(
                        Map.of(InternalTokenFilter.CONFIG_KEY, ""), "test-empty", 100))
                .build();
        resolver.registerConfig(emptyConfig, cl);
    }

    @AfterEach
    void releaseConfig() {
        if (emptyConfig != null) {
            ConfigProviderResolver.instance().releaseConfig(emptyConfig);
        }
    }

    private static ContainerRequestContext requestWithHeader(String header) {
        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        when(ctx.getHeaderString(InternalTokenFilter.HEADER)).thenReturn(header);
        return ctx;
    }

    @Test
    @DisplayName("Empty unige.internal-token config rejects matching-header request with 404 (fail-closed)")
    void emptyConfig_rejectsAnyMatchingHeader_failClosed() {
        InternalTokenFilter filter = new InternalTokenFilter(); // no override → ConfigProvider path
        assertThrows(NotFoundException.class,
                () -> filter.filter(requestWithHeader("any-value")));
    }

    @Test
    @DisplayName("Empty unige.internal-token config rejects missing-header request with 404 (fail-closed)")
    void emptyConfig_rejectsMissingHeader_failClosed() {
        InternalTokenFilter filter = new InternalTokenFilter();
        assertThrows(NotFoundException.class,
                () -> filter.filter(requestWithHeader(null)));
    }

    @Test
    @DisplayName("Empty unige.internal-token config rejects empty-header request with 404 (fail-closed)")
    void emptyConfig_rejectsEmptyHeader_failClosed() {
        InternalTokenFilter filter = new InternalTokenFilter();
        assertThrows(NotFoundException.class,
                () -> filter.filter(requestWithHeader("")));
    }
}
