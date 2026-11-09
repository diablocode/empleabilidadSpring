package mx.udg.cucea.empleabilidadspring.recommendation;

import java.util.List;

public record RecommendationResponse(
        Status status,
        List<OpportunityRecommendation> recommendations,
        List<String> missingProfileFields
) {
    public RecommendationResponse {
        recommendations = List.copyOf(recommendations);
        missingProfileFields = List.copyOf(missingProfileFields);
    }

    public enum Status {
        READY,
        INCOMPLETE_PROFILE,
        NO_MATCHES
    }

    public record OpportunityRecommendation(
            String offerId,
            String title,
            String employerName,
            String location,
            String modality,
            int mandatoryCompetenciesMatched,
            int mandatoryCompetenciesTotal,
            int mandatoryLevelsMet,
            int mandatoryLevelsComparable,
            int competenciesMatched,
            int competenciesTotal,
            List<String> matchedCompetencies,
            List<String> reasons
    ) {
        public OpportunityRecommendation {
            matchedCompetencies = List.copyOf(matchedCompetencies);
            reasons = List.copyOf(reasons);
        }
    }
}
