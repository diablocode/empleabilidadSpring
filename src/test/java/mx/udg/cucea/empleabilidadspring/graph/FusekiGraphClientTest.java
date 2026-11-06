package mx.udg.cucea.empleabilidadspring.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpServer;
import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FusekiGraphClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void reportsFusekiHttpFailuresInsteadOfReturningAnEmptyGraph() throws IOException {
        startServer(503, "text/plain", "Fuseki unavailable");
        FusekiGraphClient client = client();

        assertThrows(FusekiGraphException.class,
                () -> client.construct("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }"));
    }

    @Test
    void reportsMalformedRdfResponsesExplicitly() throws IOException {
        startServer(200, "text/turtle", "this is not valid Turtle {");
        FusekiGraphClient client = client();

        assertThrows(FusekiGraphException.class,
                () -> client.construct("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }"));
    }

    @Test
    void preservesAValidEmptyResultAsAnEmptyModel() throws IOException {
        startServer(200, "text/turtle", "# valid empty graph\n");
        FusekiGraphClient client = client();

        Model result = client.construct("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }");
        try {
            assertEquals(0, result.size());
        } finally {
            result.close();
        }
    }

    private FusekiGraphClient client() {
        return new FusekiGraphClient("http://127.0.0.1:" + server.getAddress().getPort(), "empleabilidad");
    }

    private void startServer(int status, String contentType, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/empleabilidad/sparql", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }
}
