package com.github.dimitryivaniuta.profile.api;

import com.github.dimitryivaniuta.profile.service.ProfileWriteService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Primary-region profile command API.
 */
@RestController
@RequestMapping("/api/v1/profiles")
@RequiredArgsConstructor
public class ProfileWriteController {

    private final ProfileWriteService profileWriteService;

    /**
     * Creates a new profile.
     *
     * @param request request body
     * @return created profile response
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ProfileResponse> create(@Valid @RequestBody CreateProfileRequest request) {
        return profileWriteService.create(request);
    }

    /**
     * Updates an existing profile.
     *
     * @param profileId profile identifier
     * @param request request body
     * @return updated profile response
     */
    @PutMapping("/{profileId}")
    public Mono<ProfileResponse> update(
            @PathVariable UUID profileId,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return profileWriteService.update(profileId, request);
    }

    /**
     * Deactivates a profile.
     *
     * @param profileId profile identifier
     * @return deactivated profile response
     */
    @DeleteMapping("/{profileId}")
    public Mono<ProfileResponse> deactivate(@PathVariable UUID profileId) {
        return profileWriteService.deactivate(profileId);
    }
}
