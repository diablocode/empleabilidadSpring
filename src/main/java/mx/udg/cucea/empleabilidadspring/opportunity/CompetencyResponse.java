package mx.udg.cucea.empleabilidadspring.opportunity;

import java.util.List;

public record CompetencyResponse(
        int offset,
        int limit,
        int count,
        boolean hasMore,
        List<Competency> competencies
) {
    public CompetencyResponse {
        competencies = List.copyOf(competencies);
    }

    public record Competency(String competencyId, String name) {
    }
}
