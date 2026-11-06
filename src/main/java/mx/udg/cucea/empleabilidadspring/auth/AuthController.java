package mx.udg.cucea.empleabilidadspring.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import mx.udg.cucea.empleabilidadspring.auth.AuthController.AuthResponse;
import mx.udg.cucea.empleabilidadspring.auth.AuthController.GoogleCredentialRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final GoogleCredentialVerifier credentialVerifier;
    private final SecurityContextRepository securityContextRepository;
    private final CookieCsrfTokenRepository csrfTokenRepository;

    public AuthController(
            GoogleCredentialVerifier credentialVerifier,
            SecurityContextRepository securityContextRepository,
            CookieCsrfTokenRepository csrfTokenRepository
    ) {
        this.credentialVerifier = credentialVerifier;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken csrfToken, HttpServletRequest request) {
        request.getSession(true);
        return new CsrfResponse(csrfToken.getToken());
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> signInWithGoogle(
            @RequestBody(required = false) GoogleCredentialRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        String credential = request == null ? null : request.credential();
        AuthenticatedUser user = credentialVerifier.verify(credential);

        servletRequest.getSession(true);
        servletRequest.changeSessionId();
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                user,
                null,
                java.util.List.of(() -> "ROLE_USER")
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);

        return ResponseEntity.ok(AuthResponse.from(user));
    }

    @GetMapping("/me")
    public AuthResponse currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return AuthResponse.from(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> signOut(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
            csrfTokenRepository.saveToken(null, request, response);
        return ResponseEntity.noContent().build();
    }

    public record GoogleCredentialRequest(String credential) {
    }

    public record CsrfResponse(String token) {
    }

    public record AuthResponse(String email, String name, String pictureUrl) {
        private static AuthResponse from(AuthenticatedUser user) {
            return new AuthResponse(user.email(), user.name(), user.pictureUrl());
        }
    }
}
