package ch.unige.events.event.share.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SuppressWarnings("java:S100")
class ShareResponseTest {

    @Test
    void canonicalConstructor_keepsBothFields() {
        ShareResponse res = new ShareResponse("https://share.url/abc", "abc");
        assertEquals("https://share.url/abc", res.shareUrl());
        assertEquals("abc", res.shortCode());
    }

    @Test
    void canonicalConstructor_acceptsNulls() {
        ShareResponse res = new ShareResponse(null, null);
        assertNull(res.shareUrl());
        assertNull(res.shortCode());
    }

    @Test
    void recordEqualsAndHashCode_canonicalContract() {
        ShareResponse a = new ShareResponse("u", "c");
        ShareResponse b = new ShareResponse("u", "c");
        ShareResponse c = new ShareResponse("u2", "c");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
