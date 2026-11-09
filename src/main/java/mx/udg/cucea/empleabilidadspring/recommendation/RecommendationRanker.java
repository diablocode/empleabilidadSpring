package mx.udg.cucea.empleabilidadspring.recommendation;

import static mx.udg.cucea.empleabilidadspring.graph.GraphModels.CompetencyRequirement;
import static mx.udg.cucea.empleabilidadspring.graph.GraphModels.Offer;
import static mx.udg.cucea.empleabilidadspring.profile.UserProfile.CompetencyRanking;
import static mx.udg.cucea.empleabilidadspring.recommendation.RecommendationResponse.OpportunityRecommendation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import mx.udg.cucea.empleabilidadspring.graph.EmpleabilidadVocabulary;
import mx.udg.cucea.empleabilidadspring.graph.OfferFreshness;
import mx.udg.cucea.empleabilidadspring.profile.UserProfile;

@Component
public class RecommendationRanker {

    private static final String COMPETENCY_SCALE = EmpleabilidadVocabulary.NS + "escalaCompetencia1a5";

    public List<OpportunityRecommendation> rank(UserProfile profile, List<Offer> offers, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Recommendation limit must be at least one.");
        }
        Map<String, Integer> candidateScores = new HashMap<>();
        for (CompetencyRanking competency : profile.competencies()) {
            candidateScores.put(competency.competencyIri(), competency.score());
        }

        return offers.stream()
                .filter(offer -> offer.freshness() == OfferFreshness.CURRENT)
                .map(offer -> score(offer, candidateScores))
                .filter(scored -> scored.recommendation().competenciesMatched() > 0)
                .sorted(RecommendationRanker::compare)
                .limit(limit)
                .map(ScoredRecommendation::recommendation)
                .toList();
    }

    private ScoredRecommendation score(Offer offer, Map<String, Integer> candidateScores) {
        List<CompetencyRequirement> requirements = offer.competencyRequirements().stream()
                .filter(requirement -> requirement.competency().isPresent())
                .toList();
        List<CompetencyRequirement> mandatory = requirements.stream()
                .filter(requirement -> requirement.mandatory().orElse(false))
                .toList();
        int mandatoryMatched = 0;
        int mandatoryLevelsMet = 0;
        int mandatoryLevelsComparable = 0;
        int totalMatched = 0;
        Set<String> matchedIris = new HashSet<>();
        List<String> matchedNames = new ArrayList<>();
        List<String> reasons = new ArrayList<>();

        for (CompetencyRequirement requirement : requirements) {
            var competency = requirement.competency().orElseThrow();
            Integer candidateScore = candidateScores.get(competency.iri());
            boolean isMandatory = requirement.mandatory().orElse(false);
            String name = competency.name().orElse(competency.iri());
            if (candidateScore == null) {
                if (isMandatory) {
                    reasons.add("Mandatory competency not found in your profile: " + name + ".");
                }
                continue;
            }

            totalMatched++;
            if (matchedIris.add(competency.iri())) {
                matchedNames.add(name);
            }
            if (isMandatory) {
                mandatoryMatched++;
            }

            if (requirement.expectedLevel().isEmpty()) {
                reasons.add("Matched competency: " + name + ".");
                continue;
            }
            if (requirement.scaleIri().filter(COMPETENCY_SCALE::equals).isEmpty()) {
                reasons.add("Matched competency: " + name
                        + "; its expected level was not compared because the requirement does not declare "
                        + "the supported 1–5 competency scale.");
                continue;
            }

            int expectedLevel = requirement.expectedLevel().getAsInt();
            boolean levelMet = candidateScore >= expectedLevel;
            if (isMandatory) {
                mandatoryLevelsComparable++;
                if (levelMet) {
                    mandatoryLevelsMet++;
                }
            }
            reasons.add(levelMet
                    ? "Your competency level " + candidateScore + "/5 meets the expected level "
                            + expectedLevel + "/5 for " + name + "."
                    : "Your competency level " + candidateScore + "/5 is below the expected level "
                            + expectedLevel + "/5 for " + name + ".");
        }

        if (!mandatory.isEmpty()) {
            reasons.add(0, mandatoryMatched + " of " + mandatory.size()
                    + " mandatory competency requirements matched.");
        }
        OpportunityRecommendation recommendation = new OpportunityRecommendation(
                UUID.nameUUIDFromBytes(offer.iri().getBytes(StandardCharsets.UTF_8)).toString(),
                offer.name().or(() -> offer.identifier()).orElse("Opportunity"),
                offer.employer().flatMap(employer -> employer.name()).orElse(null),
                offer.location().orElse(null),
                offer.modality().orElse(null),
                mandatoryMatched,
                mandatory.size(),
                mandatoryLevelsMet,
                mandatoryLevelsComparable,
                totalMatched,
                requirements.size(),
                matchedNames,
                reasons
        );
        return new ScoredRecommendation(recommendation, offer.iri());
    }

    private static int compare(ScoredRecommendation left, ScoredRecommendation right) {
        OpportunityRecommendation first = left.recommendation();
        OpportunityRecommendation second = right.recommendation();
        int order = compareCoverage(
                first.mandatoryCompetenciesMatched(), first.mandatoryCompetenciesTotal(),
                second.mandatoryCompetenciesMatched(), second.mandatoryCompetenciesTotal()
        );
        if (order != 0) {
            return -order;
        }
        order = compareCoverage(
                first.mandatoryLevelsMet(), first.mandatoryLevelsComparable(),
                second.mandatoryLevelsMet(), second.mandatoryLevelsComparable()
        );
        if (order != 0) {
            return -order;
        }
        order = compareCoverage(
                first.competenciesMatched(), first.competenciesTotal(),
                second.competenciesMatched(), second.competenciesTotal()
        );
        if (order != 0) {
            return -order;
        }
        return left.tieBreakIri().compareTo(right.tieBreakIri());
    }

    private static int compareCoverage(int firstNumerator, int firstDenominator, int secondNumerator,
                                       int secondDenominator) {
        long first = (long) firstNumerator * (secondDenominator == 0 ? 1 : secondDenominator);
        long second = (long) secondNumerator * (firstDenominator == 0 ? 1 : firstDenominator);
        if (firstDenominator == 0) {
            first = 0;
        }
        if (secondDenominator == 0) {
            second = 0;
        }
        return Long.compare(first, second);
    }

    private record ScoredRecommendation(OpportunityRecommendation recommendation, String tieBreakIri) {
    }
}
