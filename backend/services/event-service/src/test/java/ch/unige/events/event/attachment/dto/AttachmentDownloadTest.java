package ch.unige.events.event.attachment.dto;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SCRUM-149 follow-up — pins the hand-written {@code equals} /
 * {@code hashCode} / {@code toString} on {@link AttachmentDownload}.
 *
 * <p>The overrides exist because the default record accessors compare
 * the {@code byte[]} field by reference (Sonar java:S6218). These tests
 * enforce the value-equality contract so a future refactor cannot
 * silently regress.
 *
 * <p>{@code @QuarkusTest} is required — event-service uses
 * {@code quarkus-jacoco}, which only tracks classes loaded through the
 * QuarkusClassLoader. A plain JUnit class would execute and pass but
 * leave the record reporting near-zero coverage in the Sonar new-code
 * gate.
 */
@QuarkusTest
class AttachmentDownloadTest {

    private static final byte[] BYTES_A = {0x25, 0x50, 0x44, 0x46}; // %PDF
    private static final byte[] BYTES_A_COPY = {0x25, 0x50, 0x44, 0x46};
    private static final byte[] BYTES_B = {0x00, 0x01};

    @Test
    void equals_isReflexive() {
        AttachmentDownload a = new AttachmentDownload(BYTES_A, "application/pdf", "x.pdf");
        assertEquals(a, a);
    }

    @Test
    void equals_isTrue_whenBytesAndMetadataMatch_evenAcrossArrayInstances() {
        AttachmentDownload a = new AttachmentDownload(BYTES_A, "application/pdf", "x.pdf");
        AttachmentDownload b = new AttachmentDownload(BYTES_A_COPY, "application/pdf", "x.pdf");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void equals_isFalse_whenBytesDiffer() {
        AttachmentDownload a = new AttachmentDownload(BYTES_A, "application/pdf", "x.pdf");
        AttachmentDownload b = new AttachmentDownload(BYTES_B, "application/pdf", "x.pdf");
        assertNotEquals(a, b);
    }

    @Test
    void equals_isFalse_whenMimeTypeDiffers() {
        AttachmentDownload a = new AttachmentDownload(BYTES_A, "application/pdf", "x.pdf");
        AttachmentDownload b = new AttachmentDownload(BYTES_A, "image/png", "x.pdf");
        assertNotEquals(a, b);
    }

    @Test
    void equals_isFalse_whenFileNameDiffers() {
        AttachmentDownload a = new AttachmentDownload(BYTES_A, "application/pdf", "x.pdf");
        AttachmentDownload b = new AttachmentDownload(BYTES_A, "application/pdf", "y.pdf");
        assertNotEquals(a, b);
    }

    @Test
    void equals_isFalse_whenComparedToNullOrDifferentType() {
        AttachmentDownload a = new AttachmentDownload(BYTES_A, "application/pdf", "x.pdf");
        // Casts to Object are deliberate — the equals(Object) contract
        // must defend against unrelated runtime types ; the IDE's
        // "unlikely argument type" hint is the point we are pinning.
        Object nullRef = null;
        Object foreign = "not an attachment";
        assertFalse(a.equals(nullRef));
        assertFalse(a.equals(foreign));
    }

    @Test
    void toString_omitsRawBytes_butReportsLengthAndMetadata() {
        AttachmentDownload a = new AttachmentDownload(BYTES_A, "application/pdf", "x.pdf");
        String s = a.toString();
        assertTrue(s.contains("application/pdf"), s);
        assertTrue(s.contains("x.pdf"), s);
        assertTrue(s.contains("bytes.length=4"), s);
        // The %PDF marker must NOT leak into the toString — we explicitly
        // avoid dumping binary payloads to logs.
        assertFalse(s.contains("%PDF"), s);
    }

    @Test
    void toString_handlesNullBytesGracefully() {
        AttachmentDownload a = new AttachmentDownload(null, "application/pdf", "x.pdf");
        assertTrue(a.toString().contains("bytes.length=0"));
    }
}
