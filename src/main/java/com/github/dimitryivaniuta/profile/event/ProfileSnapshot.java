package com.github.dimitryivaniuta.profile.event;

import com.github.dimitryivaniuta.profile.domain.ProfileStatus;
import java.util.UUID;

/**
 * Complete profile state embedded in change events so regional consumers can build a read model
 * without calling the primary database.
 *
 * @param profileId profile identifier
 * @param email email address
 * @param displayName profile display name
 * @param phone optional phone number
 * @param status profile status
 */
public record ProfileSnapshot(
        UUID profileId,
        String email,
        String displayName,
        String phone,
        ProfileStatus status
) {
}
