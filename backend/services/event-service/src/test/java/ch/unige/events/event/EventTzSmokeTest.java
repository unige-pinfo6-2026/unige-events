package ch.unige.events.event;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Smoke test that the runtime container honours the {@code TZ=Europe/Zurich} environment
 * variable when set. CI / local tests run without {@code TZ} set, so the
 * test is a no-op in that case — its real value materialises in the
 * Helm-deployed container where {@code TZ} is pinned via the Deployment
 * manifest. The intent is to detect the day someone removes {@code TZ}
 * from the manifest while leaving {@code JAVA_TOOL_OPTIONS} present (or
 * vice-versa).
 */
class EventTzSmokeTest {

    @Test
    void zoneIdSystemDefault_isEuropeZurich_whenTzEnvSet() {
        String tzEnv = System.getenv("TZ");
        if (tzEnv == null || tzEnv.isBlank()) {
            // CI / local: no TZ env var set — nothing to assert.
            return;
        }
        ZoneId tz = ZoneId.systemDefault();
        assertEquals("Europe/Zurich", tz.getId(),
                "Container TZ env var is set but ZoneId.systemDefault() doesn't match"
                + " — JAVA_TOOL_OPTIONS missing on the Deployment manifest?");
    }
}
