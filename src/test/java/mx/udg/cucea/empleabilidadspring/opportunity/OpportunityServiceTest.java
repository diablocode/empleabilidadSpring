package mx.udg.cucea.empleabilidadspring.opportunity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

import mx.udg.cucea.empleabilidadspring.graph.FusekiGraphException;
import mx.udg.cucea.empleabilidadspring.graph.FusekiOfferRepository;
import mx.udg.cucea.empleabilidadspring.graph.GraphModels.Offer;
import mx.udg.cucea.empleabilidadspring.graph.OfferFreshness;

class OpportunityServiceTest {

    private final FusekiOfferRepository repository = mock(FusekiOfferRepository.class);
    private final OpportunityService service = new OpportunityService(repository);

    @Test
    void listingPagesAndFiltersOffersUsingTheFreshnessPolicy() {
        when(repository.findAllOffers()).thenReturn(List.of(
                offer("active", OfferFreshness.CURRENT),
                offer("expired", OfferFreshness.EXPIRED),
                offer("closed", OfferFreshness.CLOSED)
        ));

        var all = service.list(1, 1, false);
        var current = service.list(0, 1, true);

        assertEquals(1, all.count());
        assertEquals("EXPIRED", all.opportunities().getFirst().freshness().name());
        assertEquals(true, all.hasMore());
        assertEquals(1, current.count());
        assertEquals("CURRENT", current.opportunities().getFirst().freshness().name());
        assertEquals(false, current.hasMore());
    }

    @Test
    void offerIdsAreOpaqueAndCanRetrieveTheMatchingDetail() {
        Offer offer = offer("listed-offer", OfferFreshness.CURRENT);
        when(repository.findAllOffers()).thenReturn(List.of(offer));
        String id = OpportunityResponse.publicId(offer);

        OpportunityResponse detail = service.get(id);

        assertEquals(id, detail.offerId());
        assertEquals("Title listed-offer", detail.title());
        org.junit.jupiter.api.Assertions.assertFalse(id.contains(offer.iri()));
    }

    @Test
    void validatesPageBoundsBeforeQueryingFuseki() {
        assertThrows(OpportunityService.InvalidRequestException.class, () -> service.list(-1, 20, false));
        assertThrows(OpportunityService.InvalidRequestException.class, () -> service.list(0, 51, false));
        assertThrows(OpportunityService.InvalidRequestException.class, () -> service.list(10_001, 20, false));
        verify(repository, org.mockito.Mockito.never()).findAllOffers();
    }

    @Test
    void reportsFusekiFailureRatherThanReturningAnEmptyPage() {
        when(repository.findAllOffers()).thenThrow(
                new FusekiGraphException("Fuseki unavailable.", new RuntimeException("offline"))
        );

        assertThrows(OpportunityService.DataUnavailableException.class,
                () -> service.list(0, 20, false));
    }

    private static Offer offer(String localName, OfferFreshness freshness) {
        return new Offer(
                "https://example.test/offer/" + localName,
                Optional.of(localName),
                Optional.of("Title " + localName),
                Optional.empty(),
                List.of(),
                Optional.of("Description"),
                Optional.of("Guadalajara"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                freshness
        );
    }
}
