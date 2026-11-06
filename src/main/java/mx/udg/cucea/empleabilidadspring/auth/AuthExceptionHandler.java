package mx.udg.cucea.empleabilidadspring.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(GoogleCredentialException.class)
    ResponseEntity<ApiError> invalidCredential() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("invalid_google_credential", "Google could not verify this sign-in. Please try again."));
    }

    @ExceptionHandler(GoogleAuthenticationConfigurationException.class)
    ResponseEntity<ApiError> configurationMissing() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError(
                        "google_auth_not_configured",
                        "Google sign-in is not configured on this server. Set GOOGLE_CLIENT_ID."
                ));
    }

    public record ApiError(String code, String message) {
    }
}
