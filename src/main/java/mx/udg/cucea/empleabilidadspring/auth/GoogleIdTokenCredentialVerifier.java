package mx.udg.cucea.empleabilidadspring.auth;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GoogleIdTokenCredentialVerifier implements GoogleCredentialVerifier {

    private static final int MAX_CREDENTIAL_LENGTH = 10_000;

    private final String clientId;

    public GoogleIdTokenCredentialVerifier(@Value("${app.google.client-id:}") String clientId) {
        this.clientId = clientId;
    }

    @Override
    public AuthenticatedUser verify(String credential) {
        if (clientId == null || clientId.isBlank()) {
            throw new GoogleAuthenticationConfigurationException();
        }
        if (credential == null || credential.isBlank() || credential.length() > MAX_CREDENTIAL_LENGTH) {
            throw new GoogleCredentialException();
        }

        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance()
        )
                .setAudience(List.of(clientId))
                .build();

        try {
            GoogleIdToken idToken = verifier.verify(credential);
            if (idToken == null) {
                throw new GoogleCredentialException();
            }
            GoogleIdToken.Payload payload = idToken.getPayload();
            String subject = payload.getSubject();
            String email = payload.getEmail();
            if (subject == null || subject.isBlank()
                    || email == null || email.isBlank()
                    || !Boolean.TRUE.equals(payload.getEmailVerified())) {
                throw new GoogleCredentialException();
            }
            return new AuthenticatedUser(
                    subject,
                    email,
                    payload.get("name") instanceof String name ? name : null,
                    payload.get("picture") instanceof String picture ? picture : null
            );
        } catch (IOException | GeneralSecurityException | IllegalArgumentException exception) {
            throw new GoogleCredentialException(exception);
        }
    }
}
