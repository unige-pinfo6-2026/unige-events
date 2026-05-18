package ch.unige.events.notification.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReadAllResponseTest {

    @Test
    void recordExposesUpdatedCount() {
        ReadAllResponse r = new ReadAllResponse(12L);
        assertEquals(12L, r.updated());
    }

    @Test
    void zeroUpdated_isLegalAndIdempotent() {
        ReadAllResponse r = new ReadAllResponse(0L);
        assertEquals(0L, r.updated());
    }
}
