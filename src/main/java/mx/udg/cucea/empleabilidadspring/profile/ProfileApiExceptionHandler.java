package mx.udg.cucea.empleabilidadspring.profile;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import mx.udg.cucea.empleabilidadspring.graph.FusekiGraphException;
import mx.udg.cucea.empleabilidadspring.graph.GraphMappingException;

@RestControllerAdvice
public class ProfileApiExceptionHandler {

    @ExceptionHandler(ProfileNotFoundException.class)
    ResponseEntity<ApiError> profileNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("profile_not_found", "Create a profile before requesting it."));
    }

    @ExceptionHandler(ProfileValidationException.class)
    ResponseEntity<ApiError> invalidProfile(ProfileValidationException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("invalid_profile", exception.getMessage()));
    }

    @ExceptionHandler(FusekiGraphException.class)
    ResponseEntity<ApiError> graphUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("profile_store_unavailable", "The profile graph is temporarily unavailable."));
    }

    @ExceptionHandler(GraphMappingException.class)
    ResponseEntity<ApiError> invalidGraph() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError("profile_graph_invalid", "The stored profile graph is invalid."));
    }

    public record ApiError(String code, String message) {
    }
}
