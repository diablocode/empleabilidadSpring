package mx.udg.cucea.empleabilidadspring.auth;

public class GoogleCredentialException extends RuntimeException {

    public GoogleCredentialException() {
        super("The Google credential is invalid or expired.");
    }

    public GoogleCredentialException(Throwable cause) {
        super("The Google credential could not be verified.", cause);
    }
}
