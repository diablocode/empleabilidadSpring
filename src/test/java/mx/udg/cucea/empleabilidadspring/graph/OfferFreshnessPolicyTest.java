package mx.udg.cucea.empleabilidadspring.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class OfferFreshnessPolicyTest {

    private final OfferFreshnessPolicy policy = new OfferFreshnessPolicy(
            Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void classifiesExpiredAndExcludedStatusOffers() {
        assertEquals(OfferFreshness.EXPIRED, policy.classify(
                java.time.LocalDate.parse("2026-11-05"), "abierta"));
        assertEquals(OfferFreshness.CLOSED, policy.classify(
                java.time.LocalDate.parse("2026-12-01"), "cubierta"));
        assertEquals(OfferFreshness.CLOSED, policy.classify(null, " EXPIRADA "));
        assertEquals(OfferFreshness.EXPIRED, policy.classify(
                java.time.LocalDate.parse("2026-11-05"), "por confirmar"));
    }

    @Test
    void requiresEvidenceThatAnOfferIsCurrent() {
        assertEquals(OfferFreshness.CURRENT, policy.classify(
                java.time.LocalDate.parse("2026-11-06"), null));
        assertEquals(OfferFreshness.CURRENT, policy.classify(null, "vigente"));
        assertEquals(OfferFreshness.INDETERMINATE, policy.classify(null, null));
        assertEquals(OfferFreshness.UNKNOWN_STATUS, policy.classify(
                java.time.LocalDate.parse("2026-12-01"), "por confirmar"));
    }
}
