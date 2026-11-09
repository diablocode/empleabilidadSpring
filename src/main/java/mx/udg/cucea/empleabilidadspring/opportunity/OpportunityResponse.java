package mx.udg.cucea.empleabilidadspring.opportunity;

import java.time.LocalDate;
import java.util.List;
import mx.udg.cucea.empleabilidadspring.graph.EmpleabilidadVocabulary;
import mx.udg.cucea.empleabilidadspring.graph.GraphModels.CompetencyRequirement;
import mx.udg.cucea.empleabilidadspring.graph.GraphModels.Offer;
import mx.udg.cucea.empleabilidadspring.graph.OfferFreshness;

public record OpportunityResponse(
        String offerId,
        String title,
        String employerName,
        String description,
        String location,
        String modality,
        LocalDate expiresOn,
        Freshness freshness,
        List<Requirement> requirements
) {
    public OpportunityResponse {
        requirements = List.copyOf(requirements);
    }

    static OpportunityResponse from(Offer offer) {
        return new OpportunityResponse(
                publicId(offer),
                offer.name().or(() -> offer.identifier()).orElse("Opportunity"),
                offer.employer().flatMap(employer -> employer.name()).orElse(null),
                offer.description().orElse(null),
                offer.location().orElse(null),
                offer.modality().orElse(null),
                offer.endDate().orElse(null),
                Freshness.from(offer.freshness()),
                offer.competencyRequirements().stream().map(Requirement::from).toList()
        );
    }

    static String publicId(Offer offer) {
        return PublicResourceIds.fromIri(offer.iri());
    }

    public enum Freshness {
        CURRENT,
        EXPIRED,
        CLOSED,
        UNKNOWN_STATUS,
        INDETERMINATE;

        private static Freshness from(OfferFreshness freshness) {
            return valueOf(freshness.name());
        }
    }

    public record Requirement(
            String competencyId,
            String competency,
            Integer expectedLevel,
            String expectedLevelScale,
            Boolean mandatory
    ) {
        private static Requirement from(CompetencyRequirement requirement) {
            String scale = requirement.scaleIri()
                    .filter((EmpleabilidadVocabulary.NS + "escalaCompetencia1a5")::equals)
                    .map(ignored -> "COMPETENCY_1_TO_5")
                    .orElse(null);
            return new Requirement(
                    requirement.competency()
                            .map(competency -> PublicResourceIds.fromIri(competency.iri()))
                            .orElse(null),
                    requirement.competency()
                            .flatMap(competency -> competency.name())
                            .orElse(null),
                    requirement.expectedLevel().isPresent() ? requirement.expectedLevel().getAsInt() : null,
                    scale,
                    requirement.mandatory().orElse(null)
            );
        }
    }

    public record Page(
            int offset,
            int limit,
            int count,
            boolean hasMore,
            List<OpportunityResponse> opportunities
    ) {
        public Page {
            opportunities = List.copyOf(opportunities);
        }
    }
}
