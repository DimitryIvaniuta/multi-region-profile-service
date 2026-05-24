package com.github.dimitryivaniuta.profile.domain;

/**
 * Application-level profile status. The database intentionally stores this as VARCHAR without a
 * restrictive CHECK constraint so service code remains the policy owner.
 */
public enum ProfileStatus {
    /** Profile can be served to clients. */
    ACTIVE,

    /** Profile was deactivated but retained for audit/read-model convergence. */
    DEACTIVATED
}
