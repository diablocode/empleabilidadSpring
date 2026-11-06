package mx.udg.cucea.empleabilidadspring.auth;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GoogleIdTokenCredentialVerifierTest {

    @Test
    void requiresConfiguredAudienceBeforeAcceptingCredentials() {
        GoogleIdTokenCredentialVerifier verifier = new GoogleIdTokenCredentialVerifier(" ");

        assertThrows(GoogleAuthenticationConfigurationException.class,
                () -> verifier.verify("any-token"));
    }

    @Test
    void rejectsMissingOrMalformedGoogleCredentials() {
        GoogleIdTokenCredentialVerifier verifier =
                new GoogleIdTokenCredentialVerifier("configured-client.apps.googleusercontent.com");

        assertThrows(GoogleCredentialException.class, () -> verifier.verify(null));
        assertThrows(GoogleCredentialException.class, () -> verifier.verify(" "));
        assertThrows(GoogleCredentialException.class, () -> verifier.verify("not-a-jwt"));
    }
}
