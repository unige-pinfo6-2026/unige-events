package ch.unige.events.service;

import ch.unige.events.dto.coorganizer.CoOrganizerDTO;
import ch.unige.events.dto.coorganizer.CoOrganizerInvitationDTO;
import ch.unige.events.entity.CoOrganizerStatus;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mock
@ApplicationScoped
public class EventCoOrganizerServiceMock extends EventCoOrganizerService {

    public static volatile boolean forceNotFoundOnInvite = false;
    public static volatile boolean forceForbiddenOnInvite = false;
    public static volatile boolean forceConflictOnInvite = false;
    public static volatile boolean forceCannotInviteSelf = false;
    public static volatile boolean forceNoPendingOnAccept = false;
    public static volatile boolean forceNoPendingOnDecline = false;
    public static volatile boolean forceForbiddenOnRemove = false;
    public static volatile boolean forceNotFoundOnList = false;
    public static volatile boolean forceNotFoundOnGetMyInvitations = false;

    public static final List<CoOrganizerDTO> coOrganizersFixture = new ArrayList<>();
    public static final List<CoOrganizerInvitationDTO> myInvitationsFixture = new ArrayList<>();

    public void reset() {
        forceNotFoundOnInvite = false;
        forceForbiddenOnInvite = false;
        forceConflictOnInvite = false;
        forceCannotInviteSelf = false;
        forceNoPendingOnAccept = false;
        forceNoPendingOnDecline = false;
        forceForbiddenOnRemove = false;
        forceNotFoundOnList = false;
        forceNotFoundOnGetMyInvitations = false;
        coOrganizersFixture.clear();
        myInvitationsFixture.clear();
    }

    @Override
    public CoOrganizerDTO invite(Long eventId, String inviterAuth0Id, UUID targetUserId, boolean isAdmin) {
        if (forceNotFoundOnInvite) throw new NotFoundException();
        if (forceForbiddenOnInvite) throw new ForbiddenException();
        if (forceCannotInviteSelf) throw badRequest("cannot_invite_self",
                "The event creator cannot invite themselves as co-organizer.");
        if (forceConflictOnInvite) throw conflict("already_invited",
                "This user already has a PENDING or ACCEPTED invitation on this event.");
        return new CoOrganizerDTO(1L, targetUserId, "Mocked", null,
                CoOrganizerStatus.PENDING, LocalDateTime.now());
    }

    @Override
    public CoOrganizerDTO accept(Long eventId, String userAuth0Id) {
        if (forceNoPendingOnAccept) throw unprocessable("no_pending_invitation",
                "No pending co-organizer invitation found for this event.");
        return new CoOrganizerDTO(1L, UUID.randomUUID(), "Mocked", null,
                CoOrganizerStatus.ACCEPTED, LocalDateTime.now());
    }

    @Override
    public void decline(Long eventId, String userAuth0Id) {
        if (forceNoPendingOnDecline) throw unprocessable("no_pending_invitation",
                "No pending co-organizer invitation found for this event.");
    }

    @Override
    public void remove(Long eventId, String requesterAuth0Id, UUID targetUserId, boolean isAdmin) {
        if (forceForbiddenOnRemove) throw new ForbiddenException();
    }

    @Override
    public List<CoOrganizerDTO> getCoOrganizers(Long eventId) {
        if (forceNotFoundOnList) throw new NotFoundException();
        return List.copyOf(coOrganizersFixture);
    }

    @Override
    public List<CoOrganizerInvitationDTO> getMyInvitations(
            String auth0Id, CoOrganizerStatus status, int page, int size) {
        if (forceNotFoundOnGetMyInvitations) throw new NotFoundException();
        return List.copyOf(myInvitationsFixture);
    }
}
