package mx.udg.cucea.empleabilidadspring.graph;

public class GraphMappingException extends RuntimeException {

    public GraphMappingException(String message) {
        super(message);
    }

    public GraphMappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
