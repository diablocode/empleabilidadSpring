package mx.udg.cucea.empleabilidadspring.auth;

@FunctionalInterface
public interface GoogleCredentialVerifier {

    AuthenticatedUser verify(String credential);
}
