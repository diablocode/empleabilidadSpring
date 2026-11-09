package mx.udg.cucea.empleabilidadspring.graph;

import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.vocabulary.RDF;
import org.springframework.stereotype.Repository;

import mx.udg.cucea.empleabilidadspring.graph.GraphModels.Competency;

@Repository
public class FusekiCompetencyRepository {

    private static final int MAX_PAGE_SIZE = 101;
    private static final int MAX_OFFSET = 10_000;

    private final FusekiGraphClient graphClient;
    private final RdfDomainMapper mapper;

    public FusekiCompetencyRepository(FusekiGraphClient graphClient, RdfDomainMapper mapper) {
        this.graphClient = graphClient;
        this.mapper = mapper;
    }

    public List<Competency> findPage(int offset, int limit) {
        if (offset < 0 || offset > MAX_OFFSET) {
            throw new IllegalArgumentException("Competency catalog offset must be between 0 and 10000.");
        }
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Competency catalog page size must be between 1 and 101.");
        }
        String query = """
                PREFIX emp: <%s>
                CONSTRUCT { ?competency ?predicate ?value }
                WHERE {
                    {
                        SELECT DISTINCT ?competency WHERE {
                            ?competency a emp:Competencia
                        }
                        ORDER BY STR(?competency)
                        LIMIT %d
                        OFFSET %d
                    }
                    ?competency ?predicate ?value
                }
                """.formatted(EmpleabilidadVocabulary.NS, limit, offset);
        Model model = graphClient.construct(query);
        try {
            return model.listResourcesWithProperty(
                            RDF.type,
                            model.createResource(EmpleabilidadVocabulary.NS + "Competencia")
                    )
                    .toList()
                    .stream()
                    .map(resource -> mapper.mapCompetency(model, resource))
                    .sorted((left, right) -> left.iri().compareTo(right.iri()))
                    .toList();
        } finally {
            model.close();
        }
    }
}
