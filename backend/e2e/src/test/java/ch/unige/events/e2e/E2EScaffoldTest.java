package ch.unige.events.e2e;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Sentinel scaffold test for the e2e module. Confirms the module is
 * built and tests are picked up by surefire. The real cross-service
 * happy-path test lives in Étape 6.5 and runs against a deployed
 * preview cluster (Kong + Kafka + 4 services).
 */
class E2EScaffoldTest {

    @Test
    void scaffoldIsAlive() {
        assertNotNull(getClass().getName());
    }
}
