package ch.unige.events.event.dto;

import ch.unige.events.shared.domain.enums.RecurrenceFrequency;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SuppressWarnings("java:S100")
class RecurrenceRequestTest {

    @Test
    void canonicalConstructor_acceptsAllFields() {
        LocalDate end = LocalDate.of(2099, Month.JUNE, 1);
        RecurrenceRequest req = new RecurrenceRequest(RecurrenceFrequency.WEEKLY, end, 12);
        assertEquals(RecurrenceFrequency.WEEKLY, req.frequency());
        assertEquals(end, req.endDate());
        assertEquals(12, req.maxOccurrences());
    }

    @Test
    void canonicalConstructor_optionalsMayBeNull() {
        RecurrenceRequest req = new RecurrenceRequest(RecurrenceFrequency.MONTHLY, null, null);
        assertEquals(RecurrenceFrequency.MONTHLY, req.frequency());
        assertNull(req.endDate());
        assertNull(req.maxOccurrences());
    }

    @Test
    void recordEqualsAndHashCode_canonicalContract() {
        LocalDate end = LocalDate.of(2099, Month.JUNE, 1);
        RecurrenceRequest a = new RecurrenceRequest(RecurrenceFrequency.WEEKLY, end, 4);
        RecurrenceRequest b = new RecurrenceRequest(RecurrenceFrequency.WEEKLY, end, 4);
        RecurrenceRequest c = new RecurrenceRequest(RecurrenceFrequency.MONTHLY, end, 4);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
