package ch.unige.events.contracts;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Sentinel scaffold test for the contract-tests module. Confirms the
 * module is built and tests are picked up by surefire even before any
 * Pact contract is defined. Real Pact tests are added in Étapes 6.1–6.4.
 */
class ContractTestsScaffoldTest {

    @Test
    void scaffoldIsAlive() {
        assertNotNull(getClass().getName());
    }
}
