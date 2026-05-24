package com.github.dimitryivaniuta.profile.service;

import com.github.dimitryivaniuta.profile.api.CreateProfileRequest;
import com.github.dimitryivaniuta.profile.api.ProfileResponse;
import com.github.dimitryivaniuta.profile.api.UpdateProfileRequest;
import com.github.dimitryivaniuta.profile.config.RegionProperties;
import com.github.dimitryivaniuta.profile.domain.ProfileEventType;
import com.github.dimitryivaniuta.profile.domain.ProfileStatus;
import com.github.dimitryivaniuta.profile.event.ProfileChangeEvent;
import com.github.dimitryivaniuta.profile.exception.ConflictException;
import com.github.dimitryivaniuta.profile.exception.NotFoundException;
import com.github.dimitryivaniuta.profile.exception.WriteNotAllowedException;
import com.github.dimitryivaniuta.profile.persistence.OutboxRow;
import com.github.dimitryivaniuta.profile.persistence.ProfileRow;
import com.github.dimitryivaniuta.profile.persistence.ProfileStore;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

/**
 * Handles profile commands in the primary region and records matching outbox events atomically.
 */
@Service
@RequiredArgsConstructor
public class ProfileWriteService {

    private final ProfileStore profileStore;
    private final ProfileMapper profileMapper;
    private final RegionProperties regionProperties;
    private final ProfileInputNormalizer inputNormalizer;
    private final TransactionalOperator transactionalOperator;

    /**
     * Creates a profile in the primary write model.
     *
     * @param request create-profile command
     * @return created profile response
     */
    public Mono<ProfileResponse> create(CreateProfileRequest request) {
        ensurePrimary();
        String email = inputNormalizer.normalizeEmail(request.email());
        String displayName = inputNormalizer.normalizeDisplayName(request.displayName());
        String phone = inputNormalizer.normalizePhone(request.phone());
        Instant now = Instant.now();
        ProfileRow row = new ProfileRow(UUID.randomUUID(), email, displayName, phone, ProfileStatus.ACTIVE, 1L, now, now);
        ProfileChangeEvent event = profileMapper.toEvent(row, ProfileEventType.PROFILE_CREATED, now);
        OutboxRow outbox = profileMapper.toOutbox(event);

        return transactionalOperator.transactional(
                profileStore.findProfileByEmail(email)
                        .flatMap(existing -> Mono.<ProfileRow>error(new ConflictException("Profile with this email already exists")))
                        .switchIfEmpty(profileStore.insertProfile(row)
                                .then(profileStore.insertOutbox(outbox))
                                .thenReturn(row))
        ).map(profileMapper::toProfileResponse);
    }

    /**
     * Updates mutable profile fields and emits a new profile version.
     *
     * @param profileId profile identifier
     * @param request update command
     * @return updated profile response
     */
    public Mono<ProfileResponse> update(UUID profileId, UpdateProfileRequest request) {
        ensurePrimary();
        return profileStore.findProfileById(profileId)
                .switchIfEmpty(Mono.error(new NotFoundException("Profile not found")))
                .flatMap(existing -> {
                    Instant now = Instant.now();
                    ProfileRow updated = new ProfileRow(
                            existing.id(),
                            existing.email(),
                            inputNormalizer.normalizeDisplayName(request.displayName()),
                            inputNormalizer.normalizePhone(request.phone()),
                            existing.status(),
                            existing.version() + 1,
                            existing.createdAt(),
                            now
                    );
                    ProfileChangeEvent event = profileMapper.toEvent(updated, ProfileEventType.PROFILE_UPDATED, now);
                    return persistMutation(updated, existing.version(), event);
                });
    }

    /**
     * Deactivates a profile while retaining it for audit and read-model convergence.
     *
     * @param profileId profile identifier
     * @return deactivated profile response
     */
    public Mono<ProfileResponse> deactivate(UUID profileId) {
        ensurePrimary();
        return profileStore.findProfileById(profileId)
                .switchIfEmpty(Mono.error(new NotFoundException("Profile not found")))
                .flatMap(existing -> {
                    if (existing.status() == ProfileStatus.DEACTIVATED) {
                        return Mono.just(profileMapper.toProfileResponse(existing));
                    }
                    Instant now = Instant.now();
                    ProfileRow updated = new ProfileRow(
                            existing.id(),
                            existing.email(),
                            existing.displayName(),
                            existing.phone(),
                            ProfileStatus.DEACTIVATED,
                            existing.version() + 1,
                            existing.createdAt(),
                            now
                    );
                    ProfileChangeEvent event = profileMapper.toEvent(updated, ProfileEventType.PROFILE_DEACTIVATED, now);
                    return persistMutation(updated, existing.version(), event);
                });
    }

    private Mono<ProfileResponse> persistMutation(ProfileRow updated, long previousVersion, ProfileChangeEvent event) {
        OutboxRow outbox = profileMapper.toOutbox(event);
        return transactionalOperator.transactional(
                        profileStore.updateProfile(updated, previousVersion)
                                .flatMap(updatedRows -> updatedRows
                                        ? profileStore.insertOutbox(outbox).thenReturn(updated)
                                        : Mono.error(new ConflictException("Profile was modified concurrently")))
                )
                .map(profileMapper::toProfileResponse);
    }

    private void ensurePrimary() {
        if (!regionProperties.isPrimary()) {
            throw new WriteNotAllowedException("Profile writes are allowed only in the primary region");
        }
    }
}
