package mx.udg.cucea.empleabilidadspring;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final FusekiHealthService fusekiHealthService;

    public HealthController(FusekiHealthService fusekiHealthService) {
        this.fusekiHealthService = fusekiHealthService;
    }

    @GetMapping
    public HealthResponse applicationHealth() {
        return new HealthResponse("UP", "empleabilidad-api");
    }

    @GetMapping("/fuseki")
    public ResponseEntity<HealthResponse> fusekiHealth() {
        boolean available = fusekiHealthService.isAvailable();
        HealthResponse response = new HealthResponse(
                available ? "UP" : "DOWN",
                "fuseki"
        );
        return ResponseEntity.status(available ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(response);
    }

    public record HealthResponse(String status, String service) {
    }
}
