package mx.udg.cucea.empleabilidadspring.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import mx.udg.cucea.empleabilidadspring.FusekiHealthService;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "FUSEKI_INTEGRATION", matches = "true")
class FusekiOfferRepositoryIntegrationTest {

    @Autowired
    private FusekiOfferRepository repository;

    @Autowired
    private FusekiHealthService healthService;

    @Test
    void readsOffersFromTheConfiguredFusekiDataset() {
        assertTrue(healthService.isAvailable());
        var offers = repository.findPage(0, 20);
        assertTrue(offers.size() <= 20);
        assertEquals(offers.size(), offers.stream().map(offer -> offer.iri()).distinct().count());
        if (!offers.isEmpty() && offers.getFirst().identifier().isPresent()) {
            assertEquals(offers.getFirst(),
                    repository.findByIdentifier(offers.getFirst().identifier().orElseThrow()).orElseThrow());
        }
        assertEquals(java.util.Optional.empty(), repository.findByIdentifier(
                "missing' } UNION { ?offer ?p ?o } #"));
    }
}
