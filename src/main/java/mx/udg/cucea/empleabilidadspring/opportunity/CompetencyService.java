package mx.udg.cucea.empleabilidadspring.opportunity;

import java.util.List;

import org.springframework.stereotype.Service;

import mx.udg.cucea.empleabilidadspring.graph.FusekiCompetencyRepository;
import mx.udg.cucea.empleabilidadspring.graph.FusekiGraphException;
import mx.udg.cucea.empleabilidadspring.graph.GraphMappingException;
import mx.udg.cucea.empleabilidadspring.opportunity.CompetencyResponse.Competency;

@Service
public class CompetencyService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_OFFSET = 10_000;

    private final FusekiCompetencyRepository repository;

    public CompetencyService(FusekiCompetencyRepository repository) {
        this.repository = repository;
    }

    public CompetencyResponse list(int offset, int limit) {
        if (offset < 0 || offset > MAX_OFFSET) {
            throw new OpportunityService.InvalidRequestException("offset must be between 0 and 10000.");
        }
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new OpportunityService.InvalidRequestException("limit must be between 1 and 100.");
        }
        try {
            List<mx.udg.cucea.empleabilidadspring.graph.GraphModels.Competency> result =
                    repository.findPage(offset, limit + 1);
            List<Competency> page = result.stream()
                    .limit(limit)
                    .map(competency -> new Competency(
                            PublicResourceIds.fromIri(competency.iri()),
                            competency.name().orElse(competency.identifier().orElse("Competency"))
                    ))
                    .toList();
            return new CompetencyResponse(offset, limit, page.size(), result.size() > limit, page);
        } catch (FusekiGraphException exception) {
            throw new OpportunityService.DataUnavailableException(exception);
        } catch (GraphMappingException exception) {
            throw new OpportunityService.InvalidGraphException(exception);
        } catch (IllegalStateException exception) {
            throw new OpportunityService.DataLimitException(exception);
        }
    }
}
