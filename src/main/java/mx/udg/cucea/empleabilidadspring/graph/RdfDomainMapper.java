package mx.udg.cucea.empleabilidadspring.graph;

import static mx.udg.cucea.empleabilidadspring.graph.GraphModels.Candidate;
import static mx.udg.cucea.empleabilidadspring.graph.GraphModels.Competency;
import static mx.udg.cucea.empleabilidadspring.graph.GraphModels.CompetencyRanking;
import static mx.udg.cucea.empleabilidadspring.graph.GraphModels.CompetencyRequirement;
import static mx.udg.cucea.empleabilidadspring.graph.GraphModels.ControlledConcept;
import static mx.udg.cucea.empleabilidadspring.graph.GraphModels.Employer;
import static mx.udg.cucea.empleabilidadspring.graph.GraphModels.Explanation;
import static mx.udg.cucea.empleabilidadspring.graph.GraphModels.LanguageProficiency;
import static mx.udg.cucea.empleabilidadspring.graph.GraphModels.Offer;
import static mx.udg.cucea.empleabilidadspring.graph.GraphModels.Recommendation;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.springframework.stereotype.Component;

@Component
public class RdfDomainMapper {

    private static final Set<String> RECOMMENDATION_CLASSES = Set.of("Recomendacion");

    private final EmpleabilidadVocabulary vocabulary;
    private final OfferFreshnessPolicy freshnessPolicy;

    public RdfDomainMapper(EmpleabilidadVocabulary vocabulary, OfferFreshnessPolicy freshnessPolicy) {
        this.vocabulary = vocabulary;
        this.freshnessPolicy = freshnessPolicy;
    }

    public Offer mapOffer(Model model, Resource resource) {
        requireType(model, resource, "OfertaLaboral");
        Optional<LocalDate> endDate = optionalLiteral(model, resource, "fechaFin").map(
                value -> parseDate(value, resource, "fechaFin")
        );
        Optional<String> status = optionalLiteral(model, resource, "estadoOferta");

        return new Offer(
                resourceIri(resource),
                optionalLiteral(model, resource, "identificador"),
                optionalLiteral(model, resource, "nombre"),
                optionalResource(model, resource, "publicadoPor").map(value -> mapEmployer(model, value)),
                resources(model, resource, "requiereCompetencia").stream()
                        .map(requirement -> mapRequirement(model, requirement))
                        .toList(),
                optionalLiteral(model, resource, "descripcion"),
                optionalLiteral(model, resource, "ubicacion"),
                optionalLiteral(model, resource, "modalidad"),
                endDate,
                status,
                freshnessPolicy.classify(endDate.orElse(null), status.orElse(null))
        );
    }

    public Candidate mapCandidate(Model model, Resource resource) {
        requireType(model, resource, "Candidato");
        return new Candidate(
                resourceIri(resource),
                optionalLiteral(model, resource, "identificador"),
                optionalLiteral(model, resource, "nombre"),
                optionalLiteral(model, resource, "correoContacto"),
                optionalLiteral(model, resource, "telefonoContacto"),
                resources(model, resource, "tieneRanking").stream()
                        .map(ranking -> mapRanking(model, ranking))
                        .toList(),
                resources(model, resource, "dominaLenguaje").stream()
                        .map(language -> mapLanguage(model, language))
                        .toList()
        );
    }

    public Recommendation mapRecommendation(Model model, Resource resource) {
        requireAnyType(model, resource, RECOMMENDATION_CLASSES);
        Optional<LocalDate> validUntil = optionalLiteral(model, resource, "vigenteHasta").map(
                value -> parseDate(value, resource, "vigenteHasta")
        );
        return new Recommendation(
                resourceIri(resource),
                optionalResource(model, resource, "recomiendaCandidato").map(RdfDomainMapper::resourceIri),
                optionalResource(model, resource, "recomiendaOferta").map(RdfDomainMapper::resourceIri),
                optionalResource(model, resource, "tipoRecomendacion").map(value -> mapConcept(model, value)),
                optionalResource(model, resource, "tieneExplicacion")
                        .map(value -> mapExplanation(model, value)),
                optionalLiteral(model, resource, "versionModelo"),
                validUntil
        );
    }

    private Employer mapEmployer(Model model, Resource resource) {
        requireType(model, resource, "Empleador");
        return new Employer(
                resourceIri(resource),
                optionalLiteral(model, resource, "identificador"),
                optionalLiteral(model, resource, "nombre")
        );
    }

    private CompetencyRequirement mapRequirement(Model model, Resource resource) {
        requireType(model, resource, "RequerimientoDeCompetencia");
        OptionalInt expectedLevel = optionalInteger(model, resource, "nivelEsperado");
        Optional<String> scaleIri = optionalLiteral(model, resource, "escala");
        if (scaleIri.filter((EmpleabilidadVocabulary.NS + "escalaCompetencia1a5")::equals).isPresent()
                && expectedLevel.isPresent()
                && (expectedLevel.getAsInt() < 1 || expectedLevel.getAsInt() > 5)) {
            throw new GraphMappingException(
                    "A requirement level using emp:escalaCompetencia1a5 must be between 1 and 5 on "
                            + resourceIri(resource) + "."
            );
        }
        Optional<Boolean> mandatory = optionalLiteral(model, resource, "esObligatorio")
                .map(value -> parseBoolean(value, resource, "esObligatorio"));
        return new CompetencyRequirement(
                resourceIri(resource),
                optionalResource(model, resource, "especificaCompetencia")
                        .map(value -> mapCompetency(model, value)),
                expectedLevel,
                scaleIri,
                mandatory
        );
    }

    private CompetencyRanking mapRanking(Model model, Resource resource) {
        requireType(model, resource, "RankingDeCompetencia");
        OptionalInt score = optionalInteger(model, resource, "puntuacion");
        Optional<String> scaleIri = optionalLiteral(model, resource, "escala");
        if (scaleIri.filter(scale -> scale.equals(EmpleabilidadVocabulary.NS + "escalaCompetencia1a5")).isPresent()
                && score.isPresent()
                && (score.getAsInt() < 1 || score.getAsInt() > 5)) {
            throw new GraphMappingException(
                    "A score using emp:escalaCompetencia1a5 must be between 1 and 5 on "
                            + resourceIri(resource) + "."
            );
        }
        return new CompetencyRanking(
                resourceIri(resource),
                optionalResource(model, resource, "evaluaCompetencia")
                        .map(value -> mapCompetency(model, value)),
                score,
                scaleIri
        );
    }

    private LanguageProficiency mapLanguage(Model model, Resource resource) {
        requireType(model, resource, "Lenguaje");
        return new LanguageProficiency(
                resourceIri(resource),
                optionalLiteral(model, resource, "nombre"),
                optionalResource(model, resource, "nivelMCER")
                        .map(value -> mapConcept(model, value))
        );
    }

    public Competency mapCompetency(Model model, Resource resource) {
        requireType(model, resource, "Competencia");
        return new Competency(
                resourceIri(resource),
                optionalLiteral(model, resource, "identificador"),
                optionalLiteral(model, resource, "nombre")
        );
    }

    private ControlledConcept mapConcept(Model model, Resource resource) {
        if (!model.contains(resource, RDF.type, SKOS.Concept)) {
            throw new GraphMappingException(
                    "Resource " + resourceIri(resource) + " is not typed as a SKOS concept."
            );
        }
        return new ControlledConcept(
                resourceIri(resource),
                optionalLiteral(model, resource, SKOS.notation),
                optionalLiteral(model, resource, SKOS.prefLabel)
        );
    }

    private Explanation mapExplanation(Model model, Resource resource) {
        requireType(model, resource, "Explicacion");
        return new Explanation(
                resourceIri(resource),
                optionalLiteral(model, resource, "descripcion")
                        .or(() -> optionalLiteral(model, resource, RDFS.label)),
                resources(model, resource, "utilizaEvidencia").stream()
                        .map(RdfDomainMapper::resourceIri)
                        .toList()
        );
    }

    private void requireType(Model model, Resource resource, String className) {
        if (!model.contains(resource, RDF.type, vocabulary.resource(className))) {
            throw new GraphMappingException(
                    "Resource " + resourceIri(resource) + " is not typed as emp:" + className + "."
            );
        }
    }

    private void requireAnyType(Model model, Resource resource, Set<String> classNames) {
        boolean matches = classNames.stream()
                .map(vocabulary::resource)
                .anyMatch(type -> model.contains(resource, RDF.type, type));
        if (!matches) {
            throw new GraphMappingException("Resource " + resourceIri(resource) + " has an unexpected RDF type.");
        }
    }

    private Optional<String> optionalLiteral(Model model, Resource subject, String localName) {
        return optionalLiteral(model, subject, vocabulary.property(localName));
    }

    private Optional<String> optionalLiteral(Model model, Resource subject, Property property) {
        List<RDFNode> values = values(model, subject, property);
        if (values.isEmpty()) {
            return Optional.empty();
        }
        if (values.size() > 1) {
            throw new GraphMappingException(
                    "Expected at most one value for " + property.getURI() + " on " + resourceIri(subject) + "."
            );
        }
        RDFNode value = values.get(0);
        if (!value.isLiteral()) {
            throw new GraphMappingException(
                    "Expected a literal for " + property.getURI() + " on " + resourceIri(subject) + "."
            );
        }
        return Optional.of(value.asLiteral().getLexicalForm());
    }

    private Optional<Resource> optionalResource(Model model, Resource subject, String localName) {
        Property property = vocabulary.property(localName);
        List<RDFNode> values = values(model, subject, property);
        if (values.isEmpty()) {
            return Optional.empty();
        }
        if (values.size() > 1) {
            throw new GraphMappingException(
                    "Expected at most one resource for " + property.getURI() + " on " + resourceIri(subject) + "."
            );
        }
        if (!values.get(0).isResource()) {
            throw new GraphMappingException(
                    "Expected a resource for " + property.getURI() + " on " + resourceIri(subject) + "."
            );
        }
        return Optional.of(values.get(0).asResource());
    }

    private OptionalInt optionalInteger(Model model, Resource subject, String localName) {
        Property property = vocabulary.property(localName);
        Optional<String> value = optionalLiteral(model, subject, property);
        if (value.isEmpty()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(value.get()));
        } catch (NumberFormatException exception) {
            throw new GraphMappingException(
                    "Expected an integer for " + property.getURI() + " on " + resourceIri(subject) + ".",
                    exception
            );
        }
    }

    private boolean parseBoolean(String value, Resource subject, String localName) {
        if ("true".equalsIgnoreCase(value) || "1".equals(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value) || "0".equals(value)) {
            return false;
        }
        throw new GraphMappingException(
                "Expected a boolean for emp:" + localName + " on " + resourceIri(subject) + "."
        );
    }

    private List<Resource> resources(Model model, Resource subject, String localName) {
        Property property = vocabulary.property(localName);
        List<Resource> resources = new ArrayList<>();
        for (RDFNode value : values(model, subject, property)) {
            if (!value.isResource()) {
                throw new GraphMappingException(
                        "Expected resources for " + property.getURI() + " on " + resourceIri(subject) + "."
                );
            }
            resources.add(value.asResource());
        }
        return resources.stream()
                .distinct()
                .sorted(Comparator.comparing(RdfDomainMapper::resourceIri))
                .toList();
    }

    private List<RDFNode> values(Model model, Resource subject, Property property) {
        List<RDFNode> values = new ArrayList<>();
        model.listObjectsOfProperty(subject, property).forEachRemaining(values::add);
        return List.copyOf(values);
    }

    private LocalDate parseDate(String value, Resource subject, String localName) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new GraphMappingException(
                    "Expected an ISO-8601 date for emp:" + localName + " on " + resourceIri(subject) + ".",
                    exception
            );
        }
    }

    private static String resourceIri(Resource resource) {
        return resource.isURIResource() ? resource.getURI() : "_:" + resource.getId().getLabelString();
    }
}
