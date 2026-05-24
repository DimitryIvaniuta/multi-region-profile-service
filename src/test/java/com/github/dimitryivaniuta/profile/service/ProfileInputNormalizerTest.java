package com.github.dimitryivaniuta.profile.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for profile input normalization rules.
 */
class ProfileInputNormalizerTest {

    private final ProfileInputNormalizer normalizer = new ProfileInputNormalizer();

    /**
     * Email normalization keeps uniqueness checks deterministic.
     */
    @Test
    void normalizeEmailShouldTrimAndLowerCase() {
        assertThat(normalizer.normalizeEmail("  Alice@Example.COM  ")).isEqualTo("alice@example.com");
    }

    /**
     * Blank phone values are stored as null because the field is optional.
     */
    @Test
    void normalizePhoneShouldConvertBlankToNull() {
        assertThat(normalizer.normalizePhone("   ")).isNull();
    }

    /**
     * Display names are trimmed but internal spaces are preserved.
     */
    @Test
    void normalizeDisplayNameShouldTrimOnlyOuterWhitespace() {
        assertThat(normalizer.normalizeDisplayName("  Alice   Example  ")).isEqualTo("Alice   Example");
    }
}
