package com.github.dimitryivaniuta.profile.service;

import org.springframework.stereotype.Component;

/**
 * Normalizes user profile input before it reaches persistence or events.
 */
@Component
public class ProfileInputNormalizer {

    /**
     * Lowercases and trims email addresses to avoid duplicate logical identities.
     *
     * @param email raw request email
     * @return normalized email
     */
    public String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    /**
     * Trims profile display names while keeping internal spacing unchanged.
     *
     * @param displayName raw request display name
     * @return normalized display name
     */
    public String normalizeDisplayName(String displayName) {
        return displayName == null ? null : displayName.trim();
    }

    /**
     * Trims empty phone values to null so optional phone storage is consistent.
     *
     * @param phone raw phone value
     * @return normalized phone or null
     */
    public String normalizePhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return null;
        }
        return phone.trim();
    }
}
