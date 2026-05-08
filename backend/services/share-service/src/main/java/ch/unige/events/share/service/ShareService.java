package ch.unige.events.share.service;

import ch.unige.events.share.config.AppConfig;
import ch.unige.events.share.dto.ShareResponse;
import ch.unige.events.share.entity.Event;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.security.SecureRandom;

/**
 * Share token generator + resolver. Identical semantics to the legacy
 * monolith's ShareService (ch.unige.events.service.ShareService) — copied
 * here for the soft-extraction step (cf.
 * specs_archives/specs_claude/specs_microservices_migration.md decision 4).
 */
@ApplicationScoped
public class ShareService {

    private static final String ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 8;
    private final SecureRandom random = new SecureRandom();

    @Inject
    AppConfig appConfig;

    @Transactional
    public ShareResponse getShareInfo(Long eventId) {
        Event event = Event.<Event>findByIdOptional(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        if (event.shareCode == null) {
            event.shareCode = generateCode();
        }

        String shareUrl = appConfig.frontendUrl() + "/events/" + eventId;
        return new ShareResponse(shareUrl, event.shareCode);
    }

    @Transactional
    public Event resolveByShortCode(String shortCode) {
        return Event.findByShareCode(shortCode)
                .orElseThrow(() -> new NotFoundException("Share code not found"));
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
