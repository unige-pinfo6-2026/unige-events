package ch.unige.events.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "UpdateUsernameRequest", description = "Body pour PATCH /users/me/username")
public record UpdateUsernameRequest(
    @NotBlank
    @Size(min = 3, max = 30)
    @Pattern(regexp = "^[a-zA-Z0-9._-]{3,30}$",
             message = "must match ^[a-zA-Z0-9._-]{3,30}$ (will be normalized to lowercase)")
    String username
) {
}
