package mx.udg.cucea.empleabilidadspring;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalConfigurationCheck {

    public LocalConfigurationCheck(
            @Value("${app.fuseki.base-url}") String fusekiBaseUrl,
            @Value("${app.fuseki.dataset}") String fusekiDataset,
            @Value("${app.frontend.origin}") String frontendOrigin
    ) {
        requireHttpUrl(fusekiBaseUrl, "FUSEKI_BASE_URL");
        requireOrigin(frontendOrigin, "FRONTEND_ORIGIN");
        if (fusekiDataset == null || !fusekiDataset.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalStateException(
                    "FUSEKI_DATASET must be a non-empty dataset name containing only letters, numbers, '.', '_' or '-'."
            );
        }
    }

    private static void requireHttpUrl(String value, String settingName) {
        URI uri = parseHttpUrl(value, settingName);
        if (uri.getHost() == null) {
            throw invalidUrl(settingName, null);
        }
    }

    private static void requireOrigin(String value, String settingName) {
        URI uri = parseHttpUrl(value, settingName);
        if (uri.getHost() == null
                || (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !"/".equals(uri.getRawPath()))
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw invalidUrl(settingName, null);
        }
    }

    private static URI parseHttpUrl(String value, String settingName) {
        try {
            URI uri = URI.create(value);
            if ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) {
                return uri;
            }
        } catch (IllegalArgumentException exception) {
            throw invalidUrl(settingName, exception);
        }
        throw invalidUrl(settingName, null);
    }

    private static IllegalStateException invalidUrl(String settingName, Exception cause) {
        String message = settingName
                + " must be an absolute HTTP or HTTPS URL with a host; check your environment configuration.";
        return cause == null
                ? new IllegalStateException(message)
                : new IllegalStateException(message, cause);
    }
}
