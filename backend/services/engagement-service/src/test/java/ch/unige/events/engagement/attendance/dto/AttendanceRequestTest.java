package ch.unige.events.engagement.attendance.dto;

import ch.unige.events.shared.domain.enums.AttendanceStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AttendanceRequestTest {

    @Test
    void canonicalConstructor_attendingStatus_keepsField() {
        AttendanceRequest req = new AttendanceRequest(AttendanceStatus.ATTENDING);

        assertEquals(AttendanceStatus.ATTENDING, req.status());
    }

    @Test
    void canonicalConstructor_nullStatus_isAccepted() {
        // Bean-validation @NotNull is enforced at the resource layer, not in the canonical
        // constructor — the record itself accepts null and only fails downstream validation.
        AttendanceRequest req = new AttendanceRequest(null);

        assertNull(req.status());
    }

    @Test
    void recordEqualsAndHashCode_canonicalContract() {
        AttendanceRequest a = new AttendanceRequest(AttendanceStatus.WAITLISTED);
        AttendanceRequest b = new AttendanceRequest(AttendanceStatus.WAITLISTED);
        AttendanceRequest c = new AttendanceRequest(AttendanceStatus.ATTENDING);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
