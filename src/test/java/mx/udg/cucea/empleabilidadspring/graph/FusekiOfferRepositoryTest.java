package mx.udg.cucea.empleabilidadspring.graph;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FusekiOfferRepositoryTest {

    @Test
    void rejectsOutOfBoundsPagesBeforeContactingFuseki() {
        FusekiOfferRepository repository = new FusekiOfferRepository(null, null);

        assertThrows(IllegalArgumentException.class, () -> repository.findPage(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> repository.findPage(0, 51));
        assertThrows(IllegalArgumentException.class, () -> repository.findPage(10_001, 10));
        assertThrows(IllegalArgumentException.class, () -> repository.findByIdentifier(" "));
        assertThrows(IllegalArgumentException.class, () -> repository.findByIdentifier("x".repeat(129)));
    }
}
