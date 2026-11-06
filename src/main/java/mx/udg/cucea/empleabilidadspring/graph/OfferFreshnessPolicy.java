package mx.udg.cucea.empleabilidadspring.graph;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class OfferFreshnessPolicy {

    private static final Set<String> EXCLUDED_STATUSES = Set.of("cerrada", "expirada", "cubierta");
    private static final Set<String> CURRENT_STATUSES = Set.of(
            "abierta", "vigente", "publicada", "activa", "open", "active", "published"
    );

    private final Clock clock;

    public OfferFreshnessPolicy() {
        this(Clock.systemDefaultZone());
    }

    OfferFreshnessPolicy(Clock clock) {
        this.clock = clock;
    }

    public OfferFreshness classify(LocalDate endDate, String rawStatus) {
        String normalizedStatus = normalize(rawStatus);
        if (normalizedStatus != null && EXCLUDED_STATUSES.contains(normalizedStatus)) {
            return OfferFreshness.CLOSED;
        }
        if (endDate != null && endDate.isBefore(LocalDate.now(clock))) {
            return OfferFreshness.EXPIRED;
        }
        if (normalizedStatus != null && !CURRENT_STATUSES.contains(normalizedStatus)) {
            return OfferFreshness.UNKNOWN_STATUS;
        }
        if (endDate == null && normalizedStatus == null) {
            return OfferFreshness.INDETERMINATE;
        }
        return OfferFreshness.CURRENT;
    }

    private static String normalize(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return status.strip().toLowerCase(Locale.ROOT);
    }
}
