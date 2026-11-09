package mx.udg.cucea.empleabilidadspring.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import mx.udg.cucea.empleabilidadspring.auth.AuthenticatedUser;
import mx.udg.cucea.empleabilidadspring.graph.FusekiCompetencyRepository;
import mx.udg.cucea.empleabilidadspring.graph.GraphModels.Competency;
import mx.udg.cucea.empleabilidadspring.opportunity.PublicResourceIds;

class ProfileServiceTest {

    private static final String COMPETENCY_IRI = "https://example.test/competency/java";
    private final FusekiProfileRepository profiles = mock(FusekiProfileRepository.class);
    private final FusekiCompetencyRepository competencies = mock(FusekiCompetencyRepository.class);
    private final ProfileService service = new ProfileService(profiles, competencies);
    private final AuthenticatedUser user = new AuthenticatedUser(
            "google-subject", "candidate@example.com", "Candidate", null
    );

    @Test
    void resolvesPublicCatalogIdsToGraphResourcesBeforeSaving() {
        when(competencies.findPage(0, 100)).thenReturn(List.of(
                new Competency(COMPETENCY_IRI, Optional.empty(), Optional.of("Java"))
        ));
        UserProfile request = profile(PublicResourceIds.fromIri(COMPETENCY_IRI));

        UserProfile saved = service.save(user, request);

        assertEquals(COMPETENCY_IRI, saved.competencies().getFirst().competencyIri());
        verify(profiles).replace(user.subject(), saved);
    }

    @Test
    void rejectsCompetencyIdsOutsideTheCatalog() {
        when(competencies.findPage(0, 100)).thenReturn(List.of());

        assertThrows(ProfileValidationException.class, () -> service.save(user, profile("unknown-id")));
    }

    private static UserProfile profile(String competencyId) {
        return new UserProfile(
                "Candidate", "candidate@example.com", null,
                List.of(new UserProfile.CompetencyRanking(competencyId, 4)),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }
}
