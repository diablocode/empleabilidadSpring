package mx.udg.cucea.empleabilidadspring.graph;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.atlas.web.HttpException;
import org.apache.jena.shared.JenaException;
import org.apache.jena.sparql.exec.http.QueryExecutionHTTP;
import org.apache.jena.sparql.exec.http.UpdateExecutionHTTP;
import org.apache.jena.update.UpdateFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FusekiGraphClient {

    private static final long QUERY_TIMEOUT_SECONDS = 5;

    private final URI queryEndpoint;

    public FusekiGraphClient(
            @Value("${app.fuseki.base-url}") String baseUrl,
            @Value("${app.fuseki.dataset}") String dataset
    ) {
        this.queryEndpoint = URI.create(
                baseUrl.replaceAll("/+$", "") + "/" + dataset + "/sparql"
        );
    }

    public Model construct(String sparql) {
        try (QueryExecution execution = QueryExecutionHTTP.service(queryEndpoint.toString())
                .query(sparql)
                .timeout(QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()) {
            Model result = ModelFactory.createDefaultModel();
            result.add(execution.execConstruct());
            return result;
        } catch (JenaException exception) {
            throw new FusekiGraphException("Fuseki graph query failed or returned an invalid RDF response.", exception);
        } catch (HttpException exception) {
            throw new FusekiGraphException("Fuseki graph query failed or returned an invalid RDF response.", exception);
        }
    }

    public boolean ask(String sparql) {
        try (QueryExecution execution = QueryExecutionHTTP.service(queryEndpoint.toString())
                .query(sparql)
                .timeout(QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()) {
            return execution.execAsk();
        } catch (JenaException exception) {
            throw new FusekiGraphException("Fuseki graph validation query failed.", exception);
        } catch (HttpException exception) {
            throw new FusekiGraphException("Fuseki graph validation query failed.", exception);
        }
    }

    public void update(String sparql) {
        try {
            UpdateExecutionHTTP.service(updateEndpoint())
                    .update(UpdateFactory.create(sparql))
                    .build()
                    .execute();
        } catch (JenaException exception) {
            throw new FusekiGraphException("Fuseki graph update failed.", exception);
        } catch (HttpException exception) {
            throw new FusekiGraphException("Fuseki graph update failed.", exception);
        }
    }

    public boolean isAvailable() {
        try (QueryExecution execution = QueryExecutionHTTP.service(queryEndpoint.toString())
                .query("ASK {}")
                .timeout(QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()) {
            return execution.execAsk();
        } catch (JenaException exception) {
            throw new FusekiGraphException("Fuseki health query failed.", exception);
        } catch (HttpException exception) {
            throw new FusekiGraphException("Fuseki health query failed.", exception);
        }
    }

    private String updateEndpoint() {
        return queryEndpoint.toString().replaceFirst("/sparql$", "/update");
    }
}
