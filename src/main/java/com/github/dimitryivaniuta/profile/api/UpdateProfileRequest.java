package com.github.dimitryivaniuta.profile.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body used to mutate profile attributes that are safe to update.
 *
 * @param displayName updated human-readable profile name
 * @param phone updated optional phone number
 */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 160) String displayName,
        @Size(max = 64) String phone
) {
}
