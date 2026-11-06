package mx.udg.cucea.empleabilidadspring.graph;

import java.io.IOException;
import java.io.InputStream;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class EmpleabilidadVocabulary {

    public static final String NS =
            "https://localhost:3030/diablocode/grafosEmpleabilidad/ontology/empleabilidad#";
    public static final String ONTOLOGY_IRI =
            "https://localhost:3030/diablocode/grafosEmpleabilidad/ontology/empleabilidad";
    public static final String EXPECTED_VERSION = "1.1.0";

    private final Model model;

    public EmpleabilidadVocabulary() {
        this.model = ModelFactory.createDefaultModel();
        try (InputStream input = new ClassPathResource("ontology/empleabilidad.ttl").getInputStream()) {
            RDFDataMgr.read(model, input, Lang.TURTLE);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load the Empleabilidad ontology from the application classpath.",
                    exception);
        }

        Resource ontology = model.createResource(ONTOLOGY_IRI);
        if (!model.contains(ontology, OWL.versionInfo, EXPECTED_VERSION)) {
            throw new IllegalStateException(
                    "The bundled Empleabilidad ontology must declare version " + EXPECTED_VERSION + "."
            );
        }
        for (String className : new String[] {
                "Candidato", "Competencia", "RankingDeCompetencia", "OfertaLaboral",
                "Empleador", "RequerimientoDeCompetencia", "Recomendacion", "Explicacion"
        }) {
            if (!model.contains(resource(className), RDF.type, OWL.Class)) {
                throw new IllegalStateException(
                        "The bundled Empleabilidad ontology is missing required class emp:" + className + "."
                );
            }
        }
        for (String termName : new String[] {
                "publicadoPor", "requiereCompetencia", "especificaCompetencia", "nombre",
                "identificador", "fechaFin", "estadoOferta", "tipoRecomendacion", "nivelMCER"
        }) {
            if (!model.contains(resource(termName), null, (org.apache.jena.rdf.model.RDFNode) null)) {
                throw new IllegalStateException(
                        "The bundled Empleabilidad ontology is missing required term emp:" + termName + "."
                );
            }
        }
    }

    public Resource resource(String localName) {
        return model.createResource(NS + localName);
    }

    public Property property(String localName) {
        return model.createProperty(NS + localName);
    }
}
