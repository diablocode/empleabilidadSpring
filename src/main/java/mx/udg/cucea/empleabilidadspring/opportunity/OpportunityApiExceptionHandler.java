package mx.udg.cucea.empleabilidadspring.opportunity;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OpportunityApiExceptionHandler {

    @ExceptionHandler(OpportunityService.InvalidRequestException.class)
    ResponseEntity<ApiError> invalidRequest(OpportunityService.InvalidRequestException exception) {
        return ResponseEntity.badRequest().body(new ApiError("invalid_request", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> invalidParameter(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(
                new ApiError("invalid_request", "Query parameter " + exception.getName() + " has an invalid value.")
        );
    }

    @ExceptionHandler(OpportunityService.NotFoundException.class)
    ResponseEntity<ApiError> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("opportunity_not_found", "The requested opportunity was not found."));
    }

    @ExceptionHandler(OpportunityService.DataUnavailableException.class)
    ResponseEntity<ApiError> dataUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("opportunity_data_unavailable", "Opportunity data is temporarily unavailable."));
    }

    @ExceptionHandler(OpportunityService.InvalidGraphException.class)
    ResponseEntity<ApiError> invalidGraph() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError("opportunity_data_invalid", "The opportunity data is invalid."));
    }

    @ExceptionHandler(OpportunityService.DataLimitException.class)
    ResponseEntity<ApiError> dataLimit() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("opportunity_data_limit_exceeded", "The opportunity dataset exceeds the supported size."));
    }

    public record ApiError(String code, String message) {
    }
}
