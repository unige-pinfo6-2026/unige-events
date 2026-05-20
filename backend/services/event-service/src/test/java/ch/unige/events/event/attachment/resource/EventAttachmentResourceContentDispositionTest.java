package ch.unige.events.event.attachment.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SCRUM-149 follow-up — pure unit tests for the
 * {@link EventAttachmentResource#contentDisposition} helper.
 *
 * <p>RFC 6266 compliance is critical : browsers vary in how they parse
 * the {@code Content-Disposition} header, so we always emit both the
 * quoted-string ASCII fallback AND the extended {@code filename*}
 * UTF-8 form. These tests pin the format.
 *
 * <p>{@code @QuarkusTest} is required — event-service uses
 * {@code quarkus-jacoco}, which only tracks classes loaded through the
 * QuarkusClassLoader. A plain JUnit class would execute and pass but
 * leave the static helper reporting near-zero coverage in the Sonar
 * new-code gate.
 */
@QuarkusTest
class EventAttachmentResourceContentDispositionTest {

    @Test
    void contentDisposition_asciiOnly_keepsNameIntact() {
        String result = EventAttachmentResource.contentDisposition("report.pdf");
        assertTrue(result.startsWith("attachment; filename=\"report.pdf\""), result);
        assertTrue(result.contains("filename*=UTF-8''report.pdf"), result);
    }

    @Test
    void contentDisposition_strippsNonAsciiInFallback() {
        // é gets replaced with _ in the ASCII fallback ; the extended form
        // preserves it as percent-encoded UTF-8.
        String result = EventAttachmentResource.contentDisposition("résumé.pdf");
        assertTrue(result.contains("filename=\"r_sum_.pdf\""), result);
        assertTrue(result.contains("filename*=UTF-8''r%C3%A9sum%C3%A9.pdf"), result);
    }

    @Test
    void contentDisposition_stripsQuotesInFallback() {
        // Double quotes would break the quoted-string syntax — must be removed.
        String result = EventAttachmentResource.contentDisposition("the \"file\".pdf");
        assertTrue(result.contains("filename=\"the file.pdf\""), result);
    }

    @Test
    void contentDisposition_encodesSpacesAsPercent20InExtendedForm() {
        // URLEncoder defaults to + for spaces ; the helper must override
        // to %20 (RFC 3986).
        String result = EventAttachmentResource.contentDisposition("my report.pdf");
        assertTrue(result.contains("filename*=UTF-8''my%20report.pdf"), result);
        assertTrue(!result.contains("+"), result);
    }

    @Test
    void contentDisposition_alwaysStartsWithAttachment() {
        // Force-download intent — never inline.
        assertEquals("attachment", EventAttachmentResource.contentDisposition("x.pdf").split(";")[0]);
    }
}
