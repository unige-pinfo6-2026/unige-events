package ch.unige.events.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class ImageMagicBytes {

    public static final Map<String, String> MIME_TO_EXTENSION = Map.of(
            "image/jpeg", ".jpg",
            "image/png",  ".png",
            "image/webp", ".webp",
            "image/gif",  ".gif"
    );

    private ImageMagicBytes() {}

    public static boolean matches(Path file, String mimeType) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] h = in.readNBytes(12);
            return switch (mimeType) {
                case "image/jpeg" -> h.length >= 3
                        && (h[0] & 0xFF) == 0xFF
                        && (h[1] & 0xFF) == 0xD8
                        && (h[2] & 0xFF) == 0xFF;
                case "image/png" -> h.length >= 8
                        && (h[0] & 0xFF) == 0x89
                        && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                        && (h[4] & 0xFF) == 0x0D && (h[5] & 0xFF) == 0x0A
                        && (h[6] & 0xFF) == 0x1A && (h[7] & 0xFF) == 0x0A;
                case "image/webp" -> h.length >= 12
                        && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                        && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
                case "image/gif" -> h.length >= 6
                        && h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8'
                        && (h[4] == '7' || h[4] == '9') && h[5] == 'a';
                default -> false;
            };
        }
    }
}
