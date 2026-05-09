package ch.unige.events.user.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

public final class ImageFormat {

    public static final Map<String, String> MIME_TO_EXTENSION = Map.of(
            "image/jpeg", ".jpg",
            "image/png",  ".png",
            "image/webp", ".webp",
            "image/gif",  ".gif"
    );

    private static final byte[] JPEG_MAGIC   = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC    = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] RIFF_PREFIX  = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP_MARKER  = {'W', 'E', 'B', 'P'};
    private static final byte[] GIF87A_MAGIC = {'G', 'I', 'F', '8', '7', 'a'};
    private static final byte[] GIF89A_MAGIC = {'G', 'I', 'F', '8', '9', 'a'};

    private ImageFormat() {}

    public static boolean matches(Path file, String mimeType) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] h = in.readNBytes(12);
            return switch (mimeType) {
                case "image/jpeg" -> startsWith(h, JPEG_MAGIC);
                case "image/png"  -> startsWith(h, PNG_MAGIC);
                case "image/webp" -> isWebp(h);
                case "image/gif"  -> startsWith(h, GIF87A_MAGIC) || startsWith(h, GIF89A_MAGIC);
                default           -> false;
            };
        }
    }

    private static boolean isWebp(byte[] h) {
        return h.length >= 12
            && Arrays.equals(h, 0, 4, RIFF_PREFIX, 0, 4)
            && Arrays.equals(h, 8, 12, WEBP_MARKER, 0, 4);
    }

    private static boolean startsWith(byte[] h, byte[] prefix) {
        return h.length >= prefix.length
            && Arrays.equals(h, 0, prefix.length, prefix, 0, prefix.length);
    }
}
