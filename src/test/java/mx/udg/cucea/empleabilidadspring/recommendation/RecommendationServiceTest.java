package mx.udg.cucea.empleabilidadspring.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import mx.udg.cucea.empleabilidadspring.auth.AuthenticatedUser;
import mx.udg.cucea.empleabilidadspring.graph.FusekiGraphException;
import mx.udg.cucea.empleabilidadspring.graph.FusekiOfferRepository;
import mx.udg.cucea.empleabilidadspring.profile.FusekiProfileRepository;
import mx.udg.cucea.empleabilidadspring.profile.UserProfile;
import mx.udg.cucea.empleabilidadspring.recommendation.RecommendationResponse.Status;

class RecommendationServiceTest {

    private final FusekiProfileRepository profiles = mock(FusekiProfileRepository.class);
    private final FusekiOfferRepository offers = mock(FusekiOfferRepository.class);
    private final RecommendationService service = new RecommendationService(
            profiles, offers, new RecommendationRanker()
    );
    private final AuthenticatedUser user = new AuthenticatedUser(
            "google-subject", "candidate@example.com", "Candidate", null
    );

    @Test
    void missingProfileIsIncompleteAndDoesNotQueryOffers() {
        when(profiles.findBySubject(user.subject())).thenReturn(Optional.empty());

        RecommendationResponse response = service.getRecommendations(user);

        assertEquals(Status.INCOMPLETE_PROFILE, response.status());
        assertEquals(List.of("competencies"), response.missingProfileFields());
        verify(offers, never()).findAllOffers();
    }

    @Test
    void profileWithoutCompetenciesIsIncomplete() {
        when(profiles.findBySubject(user.subject())).thenReturn(Optional.of(profile(List.of())));

        RecommendationResponse response = service.getRecommendations(user);

        assertEquals(Status.INCOMPLETE_PROFILE, response.status());
        verify(offers, never()).findAllOffers();
    }

    @Test
    void completeProfileWithNoCurrentMatchesReturnsNoMatches() {
        when(profiles.findBySubject(user.subject())).thenReturn(Optional.of(profile(List.of(
                new UserProfile.CompetencyRanking("https://example.test/Java", 4)
        ))));
        when(offers.findAllOffers()).thenReturn(List.of());

        RecommendationResponse response = service.getRecommendations(user);

        assertEquals(Status.NO_MATCHES, response.status());
        assertEquals(List.of(), response.recommendations());
        assertEquals(List.of(), response.missingProfileFields());
    }

    @Test
    void fusekiFailureIsSurfacedAsRecommendationDataUnavailable() {
        when(profiles.findBySubject(user.subject())).thenReturn(Optional.of(profile(List.of(
                new UserProfile.CompetencyRanking("https://example.test/Java", 4)
        ))));
        when(offers.findAllOffers()).thenThrow(
                new FusekiGraphException("Fuseki unavailable.", new RuntimeException("offline"))
        );

        assertThrows(RecommendationService.DataUnavailableException.class,
                () -> service.getRecommendations(user));
    }

    private static UserProfile profile(List<UserProfile.CompetencyRanking> competencies) {
        return new UserProfile(
                "Candidate", "candidate@example.com", null,
                competencies, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of()
        );
    }
}
