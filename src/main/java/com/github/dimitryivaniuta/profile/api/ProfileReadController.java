package com.github.dimitryivaniuta.profile.api;

import com.github.dimitryivaniuta.profile.service.ProfileReadService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Regional profile query API.
 */
@RestController
@RequestMapping("/api/v1/profiles")
@RequiredArgsConstructor
public class ProfileReadController {

    private final ProfileReadService profileReadService;

    /**
     * Returns a profile from the regional cache/read model.
     *
     * @param profileId profile identifier
     * @param minVersion optional read-your-writes guard; rejects stale regional data below this version
     * @return eventually consistent regional profile view
     */
    @GetMapping("/{profileId}")
    public Mono<ProfileViewResponse> getProfile(
            @PathVariable UUID profileId,
            @RequestParam(required = false) Long minVersion
    ) {
        return profileReadService.getProfile(profileId, minVersion);
    }
}
