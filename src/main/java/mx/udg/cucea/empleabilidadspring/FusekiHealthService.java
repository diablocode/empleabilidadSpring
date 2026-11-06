package mx.udg.cucea.empleabilidadspring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import mx.udg.cucea.empleabilidadspring.graph.FusekiGraphClient;
import mx.udg.cucea.empleabilidadspring.graph.FusekiGraphException;

@Service
public class FusekiHealthService {

    private static final Logger logger = LoggerFactory.getLogger(FusekiHealthService.class);
    private final FusekiGraphClient graphClient;

    public FusekiHealthService(FusekiGraphClient graphClient) {
        this.graphClient = graphClient;
    }

    public boolean isAvailable() {
        try {
            return graphClient.isAvailable();
        } catch (FusekiGraphException exception) {
            logger.warn("Fuseki health query failed: {}", exception.getMessage());
            return false;
        }
    }
}
