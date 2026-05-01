package ch.unige.events.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageMagicBytesTest {

    // --- JPEG ---

    @Test
    void jpeg_validHeader_returnsTrue(@TempDir Path tmp) throws IOException {
        Path f = write(tmp, "img.jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0});
        assertTrue(ImageMagicBytes.matches(f, "image/jpeg"));
    }

    @Test
    void jpeg_wrongFirstByte_returnsFalse(@TempDir Path tmp) throws IOException {
        Path f = write(tmp, "bad.jpg", new byte[]{0x00, (byte) 0xD8, (byte) 0xFF, 0, 0, 0});
        assertFalse(ImageMagicBytes.matches(f, "image/jpeg"));
    }

    @Test
    void jpeg_tooShort_returnsFalse(@TempDir Path tmp) throws IOException {
        Path f = write(tmp, "short.jpg", new byte[]{(byte) 0xFF, (byte) 0xD8});
        assertFalse(ImageMagicBytes.matches(f, "image/jpeg"));
    }

    // --- PNG ---

    @Test
    void png_validHeader_returnsTrue(@TempDir Path tmp) throws IOException {
        Path f = write(tmp, "img.png", new byte[]{
            (byte) 0x89, 'P', 'N', 'G', (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A, 0, 0
        });
        assertTrue(ImageMagicBytes.matches(f, "image/png"));
    }

    @Test
    void png_svgContent_returnsFalse(@TempDir Path tmp) throws IOException {
        Path f = write(tmp, "svg.png", "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes());
        assertFalse(ImageMagicBytes.matches(f, "image/png"));
    }

    @Test
    void png_tooShort_returnsFalse(@TempDir Path tmp) throws IOException {
        Path f = write(tmp, "short.png", new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        assertFalse(ImageMagicBytes.matches(f, "image/png"));
    }

    // --- WebP ---

    @Test
    void webp_validHeader_returnsTrue(@TempDir Path tmp) throws IOException {
        Path f = write(tmp, "img.webp", new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'});
        assertTrue(ImageMagicBytes.matches(f, "image/webp"));
    }

    @Test
    void webp_wrongRiffMarker_returnsFalse(@TempDir Path tmp) throws IOException {
        Path f = write(tmp, "bad.webp", new byte[]{'X', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'});
        assertFalse(ImageMagicBytes.matches(f, "image/webp"));
    }

    @Test
    void webp_wrongWebpMarker_returnsFalse(@TempDir Path tmp) throws IOException {
        Path f = write(tmp, "bad2.webp", new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'X', 'E', 'B', 'P'});
        assertFalse(ImageMagicBytes.matches(f, "image/webp"));
    }

    @Test
    void webp_tooShort_returnsFalse(@TempDir Path tmp) throws IOException {
        Path f = write(tmp, "short.webp", new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0});
        assertFalse(ImageMagicBytes.matches(f, "image/webp"));
    }

    // --- GIF ---

    @Test
    void gif89a_validHeader_returnsTrue(@TempDir Path tmp) throws IOException {
        Path f = write(tmp, "img89.gif", new byte[]{'G', 'I', 'F', '8', '9', 'a', 0, 0});
        assertTrue(ImageMagicBytes.matches(f, "image/gif"));
    }

    @Test
    void gif87a_validHeader_returnsTrue(@TempDir Path tmp) throws IOException {
        Path f = write(tmp, "img87.gif", new byte[]{'G', 'I', 'F', '8', '7', 'a', 0, 0});
        assertTrue(ImageMagicBytes.matches(f, "image/gif"));
    }

    @Test
    void gif_invalidVersion_returnsFalse(@TempDir Path tmp) throws IOException {
        Path f = write(tmp, "bad.gif", new byte[]{'G', 'I', 'F', '8', 'X', 'a', 0, 0});
        assertFalse(ImageMagicBytes.matches(f, "image/gif"));
    }

    @Test
    void gif_tooShort_returnsFalse(@TempDir Path tmp) throws IOException {
        Path f = write(tmp, "short.gif", new byte[]{'G', 'I', 'F', '8', '9'});
        assertFalse(ImageMagicBytes.matches(f, "image/gif"));
    }

    // --- unknown / edge cases ---

    @Test
    void unknownMime_defaultCase_returnsFalse(@TempDir Path tmp) throws IOException {
        Path f = write(tmp, "file.bin", new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12});
        assertFalse(ImageMagicBytes.matches(f, "image/svg+xml"));
    }

    @Test
    void nonExistentFile_ioException_returnsFalse() {
        assertFalse(ImageMagicBytes.matches(Path.of("/nonexistent/missing.jpg"), "image/jpeg"));
    }

    @Test
    void emptyFile_returnsFalse(@TempDir Path tmp) throws IOException {
        Path f = write(tmp, "empty.jpg", new byte[0]);
        assertFalse(ImageMagicBytes.matches(f, "image/jpeg"));
    }

    private static Path write(Path dir, String name, byte[] content) throws IOException {
        Path f = dir.resolve(name);
        Files.write(f, content);
        return f;
    }
}
