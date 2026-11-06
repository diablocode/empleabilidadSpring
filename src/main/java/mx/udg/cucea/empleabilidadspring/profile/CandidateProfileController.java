package mx.udg.cucea.empleabilidadspring.profile;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import mx.udg.cucea.empleabilidadspring.auth.AuthenticatedUser;

@RestController
@RequestMapping("/api/profile")
public class CandidateProfileController {

    private final ProfileService profileService;

    public CandidateProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public UserProfile get(Authentication authentication) {
        return profileService.get(authenticatedUser(authentication));
    }

    @PutMapping
    public UserProfile save(@RequestBody(required = false) UserProfile profile, Authentication authentication) {
        return profileService.save(authenticatedUser(authentication), profile);
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(Authentication authentication) {
        profileService.delete(authenticatedUser(authentication));
        return ResponseEntity.noContent().build();
    }

    private AuthenticatedUser authenticatedUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED
            );
        }
        return user;
    }
}
