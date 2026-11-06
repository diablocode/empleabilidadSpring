package mx.udg.cucea.empleabilidadspring.auth;

import java.io.Serializable;

public record AuthenticatedUser(String subject, String email, String name, String pictureUrl)
        implements Serializable {
}
