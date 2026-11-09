package mx.udg.cucea.empleabilidadspring.graph;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public final class GraphModels {

    private GraphModels() {
    }

    public record Candidate(
            String iri,
            Optional<String> identifier,
            Optional<String> name,
            Optional<String> contactEmail,
            Optional<String> contactPhone,
            List<CompetencyRanking> competencyRankings,
            List<LanguageProficiency> languages
    ) {
        public Candidate {
            competencyRankings = List.copyOf(competencyRankings);
            languages = List.copyOf(languages);
        }
    }

    public record Competency(String iri, Optional<String> identifier, Optional<String> name) {
    }

    public record CompetencyRanking(
            String iri,
            Optional<Competency> competency,
            OptionalInt score,
            Optional<String> scaleIri
    ) {
    }

    public record LanguageProficiency(
            String languageIri,
            Optional<String> name,
            Optional<ControlledConcept> level
    ) {
    }

    public record Employer(String iri, Optional<String> identifier, Optional<String> name) {
    }

    public record CompetencyRequirement(
            String iri,
            Optional<Competency> competency,
            OptionalInt expectedLevel,
            Optional<String> scaleIri,
            Optional<Boolean> mandatory
    ) {
    }

    public record Offer(
            String iri,
            Optional<String> identifier,
            Optional<String> name,
            Optional<Employer> employer,
            List<CompetencyRequirement> competencyRequirements,
            Optional<String> description,
            Optional<String> location,
            Optional<String> modality,
            Optional<LocalDate> endDate,
            Optional<String> rawStatus,
            OfferFreshness freshness
    ) {
        public Offer {
            competencyRequirements = List.copyOf(competencyRequirements);
        }
    }

    public record ControlledConcept(String iri, Optional<String> notation, Optional<String> preferredLabel) {
    }

    public record Recommendation(
            String iri,
            Optional<String> candidateIri,
            Optional<String> offerIri,
            Optional<ControlledConcept> type,
            Optional<Explanation> explanation,
            Optional<String> modelVersion,
            Optional<LocalDate> validUntil
    ) {
    }

    public record Explanation(String iri, Optional<String> description, List<String> evidenceIris) {
        public Explanation {
            evidenceIris = List.copyOf(evidenceIris);
        }
    }
}
