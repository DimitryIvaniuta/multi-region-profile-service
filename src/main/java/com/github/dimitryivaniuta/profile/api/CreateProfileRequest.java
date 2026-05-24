package com.github.dimitryivaniuta.profile.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body used to create a profile in the primary write region.
 *
 * @param email unique user email address
 * @param displayName human-readable profile name
 * @param phone optional phone number in a normalized external format
 */
public record CreateProfileRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 160) String displayName,
        @Size(max = 64) String phone
) {
}
