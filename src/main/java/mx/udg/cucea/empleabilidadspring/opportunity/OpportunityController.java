package mx.udg.cucea.empleabilidadspring.opportunity;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import mx.udg.cucea.empleabilidadspring.auth.AuthenticatedUser;
import mx.udg.cucea.empleabilidadspring.opportunity.OpportunityResponse.Page;

@RestController
@RequestMapping("/api/opportunities")
public class OpportunityController {

    private final OpportunityService opportunityService;

    public OpportunityController(OpportunityService opportunityService) {
        this.opportunityService = opportunityService;
    }

    @GetMapping
    public Page list(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "false") boolean currentOnly,
            Authentication authentication
    ) {
        requireUser(authentication);
        return opportunityService.list(offset, limit, currentOnly);
    }

    @GetMapping("/{offerId}")
    public OpportunityResponse get(@PathVariable String offerId, Authentication authentication) {
        requireUser(authentication);
        return opportunityService.get(offerId);
    }

    private static void requireUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED
            );
        }
    }
}
