package mx.udg.cucea.empleabilidadspring.graph;

import java.util.List;
import java.util.Optional;

import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.rdf.model.Model;
import org.springframework.stereotype.Repository;

import mx.udg.cucea.empleabilidadspring.graph.GraphModels.Offer;

@Repository
public class FusekiOfferRepository {

    private static final int MAX_PAGE_SIZE = 50;

    private final FusekiGraphClient graphClient;
    private final RdfDomainMapper mapper;

    public FusekiOfferRepository(FusekiGraphClient graphClient, RdfDomainMapper mapper) {
        this.graphClient = graphClient;
        this.mapper = mapper;
    }

    public List<Offer> findPage(int offset, int limit) {
        validatePage(offset, limit);
        Model model = graphClient.construct(offerConstructQuery(
                "?offer a emp:OfertaLaboral",
                "ORDER BY STR(?offer) LIMIT " + limit + " OFFSET " + offset
        ));
        return mapOffers(model);
    }

    public Optional<Offer> findByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank() || identifier.length() > 128) {
            throw new IllegalArgumentException("Offer identifier must contain between 1 and 128 characters.");
        }

        ParameterizedSparqlString query = new ParameterizedSparqlString();
        query.setCommandText(offerConstructQuery(
                "?offer a emp:OfertaLaboral ; emp:identificador ?identifier . "
                        + "FILTER(?identifier = ?requestedIdentifier)",
                "LIMIT 2"
        ));
        query.setLiteral("requestedIdentifier", identifier);
        List<Offer> offers = mapOffers(graphClient.construct(query.toString()));
        if (offers.size() > 1) {
            throw new GraphMappingException("Offer identifier is not unique in the Fuseki graph.");
        }
        return offers.stream().findFirst();
    }

    private List<Offer> mapOffers(Model model) {
        try {
            return model.listResourcesWithProperty(
                            model.createProperty(org.apache.jena.vocabulary.RDF.type.getURI()),
                            model.createResource(EmpleabilidadVocabulary.NS + "OfertaLaboral")
                    )
                    .toList()
                    .stream()
                    .map(resource -> mapper.mapOffer(model, resource))
                    .sorted((left, right) -> left.iri().compareTo(right.iri()))
                    .toList();
        } finally {
            model.close();
        }
    }

    private String offerConstructQuery(String selection, String ordering) {
        return """
                PREFIX emp: <%s>
                CONSTRUCT {
                    ?offer ?offerPredicate ?offerValue .
                    ?employer ?employerPredicate ?employerValue .
                    ?requirement ?requirementPredicate ?requirementValue .
                    ?competency ?competencyPredicate ?competencyValue .
                }
                WHERE {
                    {
                        SELECT DISTINCT ?offer WHERE {
                            %s
                        }
                        %s
                    }
                    ?offer ?offerPredicate ?offerValue .
                    OPTIONAL {
                        ?offer emp:publicadoPor ?employer .
                        ?employer ?employerPredicate ?employerValue .
                    }
                    OPTIONAL {
                        ?offer emp:requiereCompetencia ?requirement .
                        ?requirement ?requirementPredicate ?requirementValue .
                        OPTIONAL {
                            ?requirement emp:especificaCompetencia ?competency .
                            ?competency ?competencyPredicate ?competencyValue .
                        }
                    }
                }
                """.formatted(EmpleabilidadVocabulary.NS, selection, ordering);
    }

    private static void validatePage(int offset, int limit) {
        if (offset < 0 || offset > 10_000) {
            throw new IllegalArgumentException("Offer page offset must be between 0 and 10000.");
        }
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Offer page size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
    }
}
