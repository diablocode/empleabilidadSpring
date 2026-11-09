package mx.udg.cucea.empleabilidadspring.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.Lang;
import org.junit.jupiter.api.Test;

import mx.udg.cucea.empleabilidadspring.graph.GraphModels.Offer;

class RdfDomainMapperTest {

    private static final String EX = EmpleabilidadVocabulary.NS;
    private final EmpleabilidadVocabulary vocabulary = new EmpleabilidadVocabulary();
    private final RdfDomainMapper mapper = new RdfDomainMapper(
            vocabulary,
            new OfferFreshnessPolicy(Clock.fixed(
                    Instant.parse("2026-11-06T12:00:00Z"),
                    ZoneOffset.UTC
            ))
    );

    @Test
    void mapsOfferEmployerAndCompetencyRequirementsWithoutConflatingTypes() {
        Model model = parse("""
                @prefix emp: <%s> .
                @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
                @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                emp:offer a emp:OfertaLaboral ;
                    emp:identificador "job-1" ;
                    emp:nombre "Backend developer" ;
                    emp:descripcion "Build backend services." ;
                    emp:fechaFin "2026-12-01"^^<http://www.w3.org/2001/XMLSchema#date> ;
                    emp:publicadoPor emp:employer ;
                    emp:requiereCompetencia emp:req .
                emp:employer a emp:Empleador ; emp:nombre "Example employer" .
                emp:req a emp:RequerimientoDeCompetencia ;
                    emp:especificaCompetencia emp:competency ;
                    emp:nivelEsperado 4 ;
                    emp:escala "%sescalaCompetencia1a5"^^xsd:anyURI ;
                    emp:esObligatorio true .
                emp:competency a emp:Competencia ; emp:nombre "Java" .
                emp:skill a emp:Habilidad ; emp:nombre "Not a competency" .
                """.formatted(EX, EX));

        Offer offer = mapper.mapOffer(model, model.createResource(EX + "offer"));

        assertEquals("Backend developer", offer.name().orElseThrow());
        assertEquals("Build backend services.", offer.description().orElseThrow());
        assertEquals("Example employer", offer.employer().orElseThrow().name().orElseThrow());
        assertEquals(1, offer.competencyRequirements().size());
        var requirement = offer.competencyRequirements().getFirst();
        assertEquals("Java", requirement.competency().orElseThrow().name().orElseThrow());
        assertTrue(requirement.expectedLevel().isPresent());
        assertEquals(4, requirement.expectedLevel().getAsInt());
        assertEquals(EX + "escalaCompetencia1a5", requirement.scaleIri().orElseThrow());
        assertEquals(true, requirement.mandatory().orElseThrow());
        assertEquals(OfferFreshness.CURRENT, offer.freshness());
    }

    @Test
    void representsMissingOptionalFieldsAndRejectsMalformedDates() {
        Model sparse = parse("""
                @prefix emp: <%s> .
                emp:offer a emp:OfertaLaboral .
                """.formatted(EX));
        Offer offer = mapper.mapOffer(sparse, sparse.createResource(EX + "offer"));
        assertTrue(offer.name().isEmpty());
        assertTrue(offer.endDate().isEmpty());
        assertEquals(OfferFreshness.INDETERMINATE, offer.freshness());

        Model invalid = parse("""
                @prefix emp: <%s> .
                emp:offer a emp:OfertaLaboral ; emp:fechaFin "next week" .
                """.formatted(EX));
        assertThrows(GraphMappingException.class,
                () -> mapper.mapOffer(invalid, invalid.createResource(EX + "offer")));
    }

    @Test
    void preservesCompetencyScaleIriForCandidateRankings() {
        Model model = parse("""
                @prefix emp: <%s> .
                @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                @prefix skos: <http://www.w3.org/2004/02/skos/core#> .
                emp:candidate a emp:Candidato ;
                    emp:tieneRanking emp:ranking ;
                    emp:dominaLenguaje emp:english .
                emp:ranking a emp:RankingDeCompetencia ;
                    emp:evaluaCompetencia emp:competency ;
                    emp:puntuacion 5 ;
                    emp:escala "%sescalaCompetencia1a5"^^xsd:anyURI .
                emp:competency a emp:Competencia ; emp:nombre "Java" .
                emp:english a emp:Lenguaje ;
                    emp:nombre "English" ;
                    emp:nivelMCER emp:nivelB2 .
                emp:nivelB2 a skos:Concept ; skos:notation "B2" ;
                    skos:prefLabel "B2 - Upper intermediate"@en .
                """.formatted(EX, EX));

        var candidate = mapper.mapCandidate(model, model.createResource(EX + "candidate"));

        assertEquals(1, candidate.competencyRankings().size());
        var ranking = candidate.competencyRankings().getFirst();
        assertEquals(5, ranking.score().getAsInt());
        assertEquals(EX + "escalaCompetencia1a5", ranking.scaleIri().orElseThrow());
        assertFalse(candidate.contactEmail().isPresent());
        var language = candidate.languages().getFirst();
        assertEquals(EX + "nivelB2", language.level().orElseThrow().iri());
        assertEquals("B2", language.level().orElseThrow().notation().orElseThrow());
        assertEquals("B2 - Upper intermediate", language.level().orElseThrow().preferredLabel().orElseThrow());
    }

    @Test
    void rejectsScoresOutsideTheirDeclaredCompetencyScale() {
        Model model = parse("""
                @prefix emp: <%s> .
                @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                emp:candidate a emp:Candidato ; emp:tieneRanking emp:ranking .
                emp:ranking a emp:RankingDeCompetencia ;
                    emp:puntuacion 6 ;
                    emp:escala "%sescalaCompetencia1a5"^^xsd:anyURI .
                """.formatted(EX, EX));

        assertThrows(GraphMappingException.class,
                () -> mapper.mapCandidate(model, model.createResource(EX + "candidate")));
    }

    @Test
    void rejectsConflictingSingleValueProperties() {
        Model model = parse("""
                @prefix emp: <%s> .
                emp:offer a emp:OfertaLaboral ;
                    emp:nombre "First" ;
                    emp:nombre "Second" .
                """.formatted(EX));

        assertThrows(GraphMappingException.class,
                () -> mapper.mapOffer(model, model.createResource(EX + "offer")));
    }

    @Test
    void mapsRecommendationConceptByIriAndNotation() {
        Model model = parse("""
                @prefix emp: <%s> .
                @prefix skos: <http://www.w3.org/2004/02/skos/core#> .
                emp:recommendation a emp:Recomendacion ;
                    emp:recomiendaOferta emp:offer ;
                    emp:tipoRecomendacion emp:ofertaCompatible ;
                    emp:tieneExplicacion emp:explanation .
                emp:ofertaCompatible a skos:Concept ;
                    skos:notation "oferta-compatible" ;
                    skos:prefLabel "Oferta compatible"@es .
                emp:explanation a emp:Explicacion ;
                    emp:descripcion "Competency requirements match." ;
                    emp:utilizaEvidencia emp:competency .
                """.formatted(EX));

        var recommendation = mapper.mapRecommendation(model, model.createResource(EX + "recommendation"));

        assertEquals(EX + "ofertaCompatible", recommendation.type().orElseThrow().iri());
        assertEquals("oferta-compatible", recommendation.type().orElseThrow().notation().orElseThrow());
        assertEquals("Competency requirements match.",
                recommendation.explanation().orElseThrow().description().orElseThrow());
        assertEquals(EX + "competency", recommendation.explanation().orElseThrow().evidenceIris().getFirst());
    }

    private static Model parse(String turtle) {
        Model model = ModelFactory.createDefaultModel();
        RDFParser.create()
                .source(new StringReader(turtle))
                .lang(Lang.TURTLE)
                .parse(model);
        return model;
    }
}
