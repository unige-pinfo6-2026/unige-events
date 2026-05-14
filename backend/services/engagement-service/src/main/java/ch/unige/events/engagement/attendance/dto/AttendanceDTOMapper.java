package ch.unige.events.engagement.attendance.dto;

import ch.unige.events.engagement.attendance.entity.Attendance;
import ch.unige.events.shared.domain.dto.AttendanceDTO;
import ch.unige.events.shared.domain.dto.AttendeeProjection;
import ch.unige.events.shared.domain.dto.UserPublicResponse;

/**
 * Mapper from {@link Attendance} JPA entity to {@link AttendanceDTO}
 * (shared record). Lives in engagement-service (not in
 * shared-domain-dtos) because it imports the JPA entity.
 *
 * <p>Décision A finalization-ultimate: replaces the service-local
 * AttendanceDTO record with the shared one consumed cross-service.
 */
public final class AttendanceDTOMapper {

    private AttendanceDTOMapper() {}

    /** Without enrichment (id-only payload, used cross-service). */
    public static AttendanceDTO from(Attendance attendance) {
        return new AttendanceDTO(
                attendance.id, attendance.userId, attendance.eventId,
                attendance.status, attendance.createdAt,
                null, null);
    }

    /** With user enrichment (display name + avatar from UserServiceClient). */
    public static AttendanceDTO from(Attendance attendance, UserPublicResponse user) {
        return new AttendanceDTO(
                attendance.id, attendance.userId, attendance.eventId,
                attendance.status, attendance.createdAt,
                user != null ? user.displayName() : null,
                user != null ? user.avatarUrl() : null);
    }

    /**
     * Privacy-aware projection used by
     * {@code AttendanceService.getAttendees}.
     *
     * <p>Organizer view ({@code isOrganizerView=true}) keeps real identity
     * for every row including private profiles. Non-organizer view returns
     * real identity only for public profiles; private-profile rows are
     * anonymized with {@code displayName=null}, {@code avatarUrl=null}, and
     * {@code userId=null} to prevent the caller from probing
     * {@code GET /users/{id}} to de-anonymize the participant.
     *
     * <p>Deleted users ({@code projection=null}) are anonymized the same
     * way regardless of caller role — there is no real identity to expose.
     */
    public static AttendanceDTO fromWithPrivacy(
            Attendance attendance, AttendeeProjection projection, boolean isOrganizerView) {
        if (projection == null) {
            return new AttendanceDTO(
                    attendance.id, null, attendance.eventId,
                    attendance.status, attendance.createdAt,
                    null, null);
        }
        boolean exposeIdentity = isOrganizerView || projection.profilePublic();
        return new AttendanceDTO(
                attendance.id,
                exposeIdentity ? attendance.userId : null,
                attendance.eventId,
                attendance.status, attendance.createdAt,
                exposeIdentity ? projection.displayName() : null,
                exposeIdentity ? projection.avatarUrl() : null);
    }
}
