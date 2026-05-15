package ch.unige.events.event.share.service;

import ch.unige.events.event.coorganizer.entity.EventCoOrganizer;
import ch.unige.events.event.entity.Event;
import ch.unige.events.event.share.config.AppConfig;
import ch.unige.events.event.share.dto.ShareResponse;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.projections.CallerIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Share token generator + resolver. Co-located in event-service post-finalization.
 *
 * <p>Apply the ISSUE-92 anti-oracle cascade + creator/accepted-co-org/admin
 * guard before generating or returning the share code so a non-authorized
 * caller cannot probe an event's existence (404 indistinguishable from
 * unknown id) nor mutate the database (no shareCode persisted on rollback).
 */
@ApplicationScoped
public class ShareService {

    private static final String ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 8;
    private final SecureRandom random = new SecureRandom();

    @Inject
    AppConfig appConfig;

    @Inject CallerIdentity callerIdentity;

    /**
     * Loads the event and authorizes the caller before generating / returning
     * the share code.
     *
     * <p>ISSUE-92 anti-oracle cascade: events in {@code DRAFT}, {@code CANCELLED},
     * {@code EXPIRED} or {@code BANNED} are 404'd for callers who are neither
     * the creator, an accepted co-organizer, nor an admin — strictly identical
     * to the unknown-id branch.
     *
     * <p>Authorization: even on a {@code PUBLISHED} event, only the creator,
     * accepted co-organizers or admins receive the share code (others get 403).
     * This prevents leaking the share secret to anonymous browsers and ensures
     * no {@code event.shareCode} mutation is committed on the rollback path.
     *
     * @throws NotFoundException event not found, or in a non-public state and
     *                           caller is not creator/co-org/admin (anti-oracle).
     * @throws ForbiddenException event is PUBLISHED but caller is not authorized.
     */
    @Transactional
    public ShareResponse getShareInfo(Long eventId, String auth0Id, boolean isAdmin) {
        Event event = Event.<Event>findByIdOptional(eventId)
                .orElseThrow(NotFoundException::new);

        if (event.status == EventStatus.BANNED) {
            throw new NotFoundException();
        }

        UUID callerUuid = callerIdentity.getUuid();
        boolean isCreator = callerUuid != null && callerUuid.equals(event.creatorId);
        boolean isAcceptedCoOrg = callerUuid != null
                && EventCoOrganizer.isAcceptedFor(eventId, callerUuid);

        if (event.status != EventStatus.PUBLISHED && !isAdmin && !isCreator && !isAcceptedCoOrg) {
            throw new NotFoundException();
        }
        if (!isAdmin && !isCreator && !isAcceptedCoOrg) {
            throw new ForbiddenException(
                    "Only the creator, accepted co-organizers or admins can fetch share info.");
        }

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
