package mx.udg.cucea.empleabilidadspring.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import mx.udg.cucea.empleabilidadspring.graph.EmpleabilidadVocabulary;
import mx.udg.cucea.empleabilidadspring.graph.GraphModels.Competency;
import mx.udg.cucea.empleabilidadspring.graph.GraphModels.CompetencyRequirement;
import mx.udg.cucea.empleabilidadspring.graph.GraphModels.Offer;
import mx.udg.cucea.empleabilidadspring.graph.OfferFreshness;
import mx.udg.cucea.empleabilidadspring.profile.UserProfile;

class RecommendationRankerTest {

    private static final String NS = EmpleabilidadVocabulary.NS;
    private static final String SCALE = NS + "escalaCompetencia1a5";
    private final RecommendationRanker ranker = new RecommendationRanker();

    @Test
    void ranksMandatoryCompetencyCoverageBeforeOtherCompetencyMatches() {
        UserProfile profile = profile(
                new UserProfile.CompetencyRanking(NS + "java", 4),
                new UserProfile.CompetencyRanking(NS + "sql", 4)
        );
        Offer oneOfTwoMandatory = offer("a", requirement("java", true, null, null),
                requirement("missing", true, null, null));
        Offer allMandatory = offer("b", requirement("java", true, null, null));

        var recommendations = ranker.rank(profile, List.of(oneOfTwoMandatory, allMandatory), 20);

        assertEquals(List.of(offerId("b"), offerId("a")),
                recommendations.stream().map(result -> result.offerId()).toList());
        assertEquals(1, recommendations.getFirst().mandatoryCompetenciesMatched());
        assertEquals(1, recommendations.getFirst().mandatoryCompetenciesTotal());
        assertEquals("Mandatory competency not found in your profile: missing.",
                recommendations.get(1).reasons().get(2));
    }

    @Test
    void comparesMandatoryLevelsOnlyWhenBothDeclareTheSameSupportedScale() {
        UserProfile profile = profile(new UserProfile.CompetencyRanking(NS + "java", 4));
        Offer met = offer("a", requirement("java", true, 4, SCALE));
        Offer unmet = offer("b", requirement("java", true, 5, SCALE));
        Offer unspecifiedScale = offer("c", requirement("java", true, 2, null));

        var recommendations = ranker.rank(profile, List.of(unmet, unspecifiedScale, met), 20);

        assertEquals(List.of(offerId("a"), offerId("b"), offerId("c")),
                recommendations.stream().map(result -> result.offerId()).toList());
        assertEquals(1, recommendations.getFirst().mandatoryLevelsMet());
        assertEquals(1, recommendations.getFirst().mandatoryLevelsComparable());
        assertEquals(0, recommendations.get(1).mandatoryLevelsMet());
        assertEquals(0, recommendations.get(2).mandatoryLevelsComparable());
        org.junit.jupiter.api.Assertions.assertTrue(
                recommendations.get(2).reasons().get(1).contains("was not compared")
        );
    }

    @Test
    void filtersNonCurrentOffersRanksTiesByIriAndBoundsResults() {
        UserProfile profile = profile(new UserProfile.CompetencyRanking(NS + "java", 4));
        Offer currentZ = offer("z", requirement("java", false, null, null));
        Offer currentA = offer("a", requirement("java", false, null, null));
        Offer expired = offer("expired", OfferFreshness.EXPIRED, requirement("java", false, null, null));
        Offer unmatched = offer("unmatched", requirement("python", false, null, null));

        var recommendations = ranker.rank(
                profile, List.of(currentZ, expired, unmatched, currentA), 1
        );

        assertEquals(1, recommendations.size());
        assertEquals(offerId("a"), recommendations.getFirst().offerId());
        assertEquals(1, recommendations.getFirst().competenciesMatched());
        assertEquals(1, recommendations.getFirst().competenciesTotal());
        assertEquals("Matched competency: java.", recommendations.getFirst().reasons().getFirst());
    }

    private static UserProfile profile(UserProfile.CompetencyRanking... competencies) {
        return new UserProfile(
                "Candidate", "candidate@example.com", null,
                List.of(competencies), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of()
        );
    }

    private static CompetencyRequirement requirement(
            String competency,
            boolean mandatory,
            Integer expectedLevel,
            String scaleIri
    ) {
        return new CompetencyRequirement(
                NS + "requirement/" + competency,
                Optional.of(new Competency(NS + competency, Optional.empty(), Optional.of(competency))),
                expectedLevel == null ? OptionalInt.empty() : OptionalInt.of(expectedLevel),
                Optional.ofNullable(scaleIri),
                Optional.of(mandatory)
        );
    }

    private static Offer offer(String localName, CompetencyRequirement... requirements) {
        return offer(localName, OfferFreshness.CURRENT, requirements);
    }

    private static Offer offer(
            String localName,
            OfferFreshness freshness,
            CompetencyRequirement... requirements
    ) {
        return new Offer(
                NS + "offer/" + localName,
                Optional.of(localName),
                Optional.of("Opportunity " + localName),
                Optional.empty(),
                List.of(requirements),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(LocalDate.parse("2027-01-01")),
                Optional.empty(),
                freshness
        );
    }

    private static String offerId(String localName) {
        return UUID.nameUUIDFromBytes((NS + "offer/" + localName).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
