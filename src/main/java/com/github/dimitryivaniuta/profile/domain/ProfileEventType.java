package com.github.dimitryivaniuta.profile.domain;

/**
 * Event types emitted by the primary region when a profile changes.
 */
public enum ProfileEventType {
    /** A profile has been created. */
    PROFILE_CREATED,

    /** Mutable profile attributes have changed. */
    PROFILE_UPDATED,

    /** A profile has been deactivated. */
    PROFILE_DEACTIVATED
}
