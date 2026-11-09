package mx.udg.cucea.empleabilidadspring.opportunity;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class PublicResourceIds {

    private PublicResourceIds() {
    }

    public static String fromIri(String iri) {
        return UUID.nameUUIDFromBytes(iri.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
