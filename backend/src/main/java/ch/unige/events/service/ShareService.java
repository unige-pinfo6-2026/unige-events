package ch.unige.events.service;

import ch.unige.events.dto.event.ShareResponse;
import ch.unige.events.entity.Event;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.SecureRandom;

@ApplicationScoped
public class ShareService {

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 8;
    private final SecureRandom random = new SecureRandom();

    @ConfigProperty(name = "app.frontend.url", defaultValue = "https://10.25.10.136.nip.io")
    String frontendUrl;

    @Transactional
    public ShareResponse getShareInfo(Long eventId) {
        Event event = Event.<Event>findByIdOptional(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        if (event.shareCode == null) {
            event.shareCode = generateCode();
        }

        String shareUrl = frontendUrl + "/events/" + eventId;
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
