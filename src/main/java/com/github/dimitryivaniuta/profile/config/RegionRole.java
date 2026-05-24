package com.github.dimitryivaniuta.profile.config;

/**
 * Runtime role of a deployed service instance.
 */
public enum RegionRole {
    /** Accepts profile writes and publishes change events. */
    PRIMARY,

    /** Serves regional reads and applies replicated profile change events. */
    READ_REPLICA
}
