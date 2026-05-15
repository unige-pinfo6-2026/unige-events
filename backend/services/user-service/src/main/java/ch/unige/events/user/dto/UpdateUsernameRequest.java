package ch.unige.events.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Body payload for {@code PATCH /users/me/username} (SCRUM-169).
 *
 * <p>The username is normalised ({@code toLowerCase().trim()}) by
 * {@code UserService.updateUsername} before the persistence check —
 * Bean Validation runs on the raw input first, so a payload with
 * leading/trailing whitespace or uppercase characters is rejected with
 * {@code 400 validation_error} <em>before</em> hitting the service.
 *
 * <p>The blocklist ({@code me, admin, api, login, logout, signup,
 * register, settings}) is enforced at the service level rather than via
 * an annotation — Bean Validation cannot easily express set-membership.
 */
@Schema(
    name = "UpdateUsernameRequest",
    description = "Body for PATCH /users/me/username (SCRUM-169)."
)
public record UpdateUsernameRequest(
    @Schema(
        description = "Public-facing handle. 3-30 chars, lowercase letters/digits/`.`/`_`/`-`. "
            + "Blocklist applies (cf. UserService.updateUsername)."
    )
    @NotBlank
    @Size(min = 3, max = 30)
    @Pattern(regexp = "^[a-z0-9._-]{3,30}$", message = "must match ^[a-z0-9._-]{3,30}$")
    String username
) {
}
