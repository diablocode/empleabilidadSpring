package mx.udg.cucea.empleabilidadspring.recommendation;

import java.util.List;

import org.springframework.stereotype.Service;

import mx.udg.cucea.empleabilidadspring.auth.AuthenticatedUser;
import mx.udg.cucea.empleabilidadspring.graph.FusekiGraphException;
import mx.udg.cucea.empleabilidadspring.graph.FusekiOfferRepository;
import mx.udg.cucea.empleabilidadspring.graph.GraphMappingException;
import mx.udg.cucea.empleabilidadspring.profile.FusekiProfileRepository;
import mx.udg.cucea.empleabilidadspring.recommendation.RecommendationResponse.OpportunityRecommendation;

@Service
public class RecommendationService {

    private static final int MAX_RESULTS = 20;

    private final FusekiProfileRepository profileRepository;
    private final FusekiOfferRepository offerRepository;
    private final RecommendationRanker ranker;

    public RecommendationService(
            FusekiProfileRepository profileRepository,
            FusekiOfferRepository offerRepository,
            RecommendationRanker ranker
    ) {
        this.profileRepository = profileRepository;
        this.offerRepository = offerRepository;
        this.ranker = ranker;
    }

    public RecommendationResponse getRecommendations(AuthenticatedUser user) {
        try {
            var profile = profileRepository.findBySubject(user.subject());
            if (profile.isEmpty()) {
                return incompleteProfile();
            }
            if (profile.orElseThrow().competencies().isEmpty()) {
                return incompleteProfile();
            }

            List<OpportunityRecommendation> recommendations = ranker.rank(
                    profile.orElseThrow(), offerRepository.findAllOffers(), MAX_RESULTS
            );
            return recommendations.isEmpty()
                    ? new RecommendationResponse(RecommendationResponse.Status.NO_MATCHES, List.of(), List.of())
                    : new RecommendationResponse(RecommendationResponse.Status.READY, recommendations, List.of());
        } catch (FusekiGraphException exception) {
            throw new DataUnavailableException(exception);
        } catch (GraphMappingException exception) {
            throw new InvalidGraphException(exception);
        }
    }

    private static RecommendationResponse incompleteProfile() {
        return new RecommendationResponse(
                RecommendationResponse.Status.INCOMPLETE_PROFILE,
                List.of(),
                List.of("competencies")
        );
    }

    public static final class DataUnavailableException extends RuntimeException {
        public DataUnavailableException(Throwable cause) {
            super(cause);
        }
    }

    public static final class InvalidGraphException extends RuntimeException {
        public InvalidGraphException(Throwable cause) {
            super(cause);
        }
    }
}
