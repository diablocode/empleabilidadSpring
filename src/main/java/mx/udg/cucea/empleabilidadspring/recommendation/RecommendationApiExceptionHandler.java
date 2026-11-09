package mx.udg.cucea.empleabilidadspring.recommendation;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RecommendationApiExceptionHandler {

    @ExceptionHandler(RecommendationService.DataUnavailableException.class)
    ResponseEntity<ApiError> graphUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError(
                        "recommendation_data_unavailable",
                        "Recommendation data is temporarily unavailable."
                ));
    }

    @ExceptionHandler(RecommendationService.InvalidGraphException.class)
    ResponseEntity<ApiError> invalidGraph() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError(
                        "recommendation_data_invalid",
                        "The recommendation graph data is invalid."
                ));
    }

    public record ApiError(String code, String message) {
    }
}
