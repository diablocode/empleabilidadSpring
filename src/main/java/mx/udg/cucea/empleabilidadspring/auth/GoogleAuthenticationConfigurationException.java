package mx.udg.cucea.empleabilidadspring.auth;

public class GoogleAuthenticationConfigurationException extends RuntimeException {

    public GoogleAuthenticationConfigurationException() {
        super("Google sign-in is not configured. Set GOOGLE_CLIENT_ID for the backend.");
    }
}
