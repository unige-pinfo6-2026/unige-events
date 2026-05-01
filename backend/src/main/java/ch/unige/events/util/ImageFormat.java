package ch.unige.events.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class ImageFormat {

    public static final Map<String, String> MIME_TO_EXTENSION = Map.of(
            "image/jpeg", ".jpg",
            "image/png",  ".png",
            "image/webp", ".webp",
            "image/gif",  ".gif"
    );

    private ImageFormat() {}

    public static boolean matches(Path file, String mimeType) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] h = in.readNBytes(12);
            return switch (mimeType) {
                case "image/jpeg" -> isJpeg(h);
                case "image/png"  -> isPng(h);
                case "image/webp" -> isWebp(h);
                case "image/gif"  -> isGif(h);
                default           -> false;
            };
        }
    }

    private static boolean isJpeg(byte[] h) {
        if (h.length < 3)          return false;
        if ((h[0] & 0xFF) != 0xFF) return false;
        if ((h[1] & 0xFF) != 0xD8) return false;
        if ((h[2] & 0xFF) != 0xFF) return false;
        return true;
    }

    private static boolean isPng(byte[] h) {
        if (h.length < 8)          return false;
        if ((h[0] & 0xFF) != 0x89) return false;
        if (h[1] != 'P')           return false;
        if (h[2] != 'N')           return false;
        if (h[3] != 'G')           return false;
        if ((h[4] & 0xFF) != 0x0D) return false;
        if ((h[5] & 0xFF) != 0x0A) return false;
        if ((h[6] & 0xFF) != 0x1A) return false;
        if ((h[7] & 0xFF) != 0x0A) return false;
        return true;
    }

    private static boolean isWebp(byte[] h) {
        if (h.length < 12) return false;
        if (h[0] != 'R')   return false;
        if (h[1] != 'I')   return false;
        if (h[2] != 'F')   return false;
        if (h[3] != 'F')   return false;
        if (h[8] != 'W')   return false;
        if (h[9] != 'E')   return false;
        if (h[10] != 'B')  return false;
        if (h[11] != 'P')  return false;
        return true;
    }

    private static boolean isGif(byte[] h) {
        if (h.length < 6)                return false;
        if (h[0] != 'G')                 return false;
        if (h[1] != 'I')                 return false;
        if (h[2] != 'F')                 return false;
        if (h[3] != '8')                 return false;
        if (h[4] != '7' && h[4] != '9') return false;
        if (h[5] != 'a')                 return false;
        return true;
    }
}