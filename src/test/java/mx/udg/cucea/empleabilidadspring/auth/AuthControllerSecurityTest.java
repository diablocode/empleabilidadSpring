package mx.udg.cucea.empleabilidadspring.auth;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "app.google.client-id=test-google-client-id")
@AutoConfigureMockMvc
class AuthControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GoogleCredentialVerifier credentialVerifier;

    @Test
    void protectedApisReturnConsistentUnauthenticatedJson() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"));
        mockMvc.perform(get("/api/profiles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsAllowsOnlyTheConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.options("/api/auth/me")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Access-Control-Allow-Credentials", "true"));

        mockMvc.perform(MockMvcRequestBuilders.options("/api/auth/me")
                        .header("Origin", "https://not-authorized.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void csrfProtectsLoginAndSessionSupportsCurrentUserAndLogout() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();
        String csrfToken = jsonPathValue(csrfResult, "token");
        MockCookie csrfCookie = new MockCookie("XSRF-TOKEN",
                csrfResult.getResponse().getCookie("XSRF-TOKEN").getValue());
        MockHttpSession firstSession = (MockHttpSession) csrfResult.getRequest().getSession(false);
        String firstSessionId = firstSession.getId();

        mockMvc.perform(post("/api/auth/google")
                        .session(firstSession)
                        .cookie(csrfCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credential\":\"test-id-token\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));

        AuthenticatedUser user = new AuthenticatedUser("google-subject-123", "person@example.com",
                "Example Person", "https://example.com/avatar.png");
        when(credentialVerifier.verify("test-id-token")).thenReturn(user);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/google")
                        .session(firstSession)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credential\":\"test-id-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("person@example.com"))
                .andExpect(jsonPath("$.name").value("Example Person"))
                .andExpect(jsonPath("$.subject").doesNotExist())
                .andReturn();

        MockHttpSession authenticatedSession = (MockHttpSession) loginResult.getRequest().getSession(false);
        String authenticatedSessionId = authenticatedSession.getId();
        org.junit.jupiter.api.Assertions.assertNotEquals(
                firstSessionId,
                authenticatedSessionId,
                "Authentication should rotate the session identifier."
        );

        mockMvc.perform(get("/api/auth/me").session(authenticatedSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("person@example.com"));

        MvcResult signOutCsrf = mockMvc.perform(get("/api/auth/csrf").session(authenticatedSession))
                .andExpect(status().isOk())
                .andReturn();
        String signOutToken = jsonPathValue(signOutCsrf, "token");
        MockCookie signOutCookie = new MockCookie("XSRF-TOKEN",
                signOutCsrf.getResponse().getCookie("XSRF-TOKEN").getValue());

        mockMvc.perform(post("/api/auth/logout")
                        .session(authenticatedSession)
                        .cookie(signOutCookie)
                        .header("X-XSRF-TOKEN", signOutToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/auth/me").session(new MockHttpSession()))
                .andExpect(status().isUnauthorized());
        verify(credentialVerifier).verify("test-id-token");
    }

    @Test
    void invalidGoogleCredentialsDoNotCreateASession() throws Exception {
        when(credentialVerifier.verify("invalid-token")).thenThrow(new GoogleCredentialException());
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        String token = jsonPathValue(csrfResult, "token");

        mockMvc.perform(post("/api/auth/google")
                        .cookie(csrfResult.getResponse().getCookie("XSRF-TOKEN"))
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credential\":\"invalid-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_google_credential"));
    }

    @Test
    void reportsMissingGoogleServerConfigurationWithoutCreatingASession() throws Exception {
        when(credentialVerifier.verify("test-id-token"))
                .thenThrow(new GoogleAuthenticationConfigurationException());
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        mockMvc.perform(post("/api/auth/google")
                        .cookie(csrfResult.getResponse().getCookie("XSRF-TOKEN"))
                        .header("X-XSRF-TOKEN", jsonPathValue(csrfResult, "token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credential\":\"test-id-token\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("google_auth_not_configured"));
    }

    private static String jsonPathValue(MvcResult result, String property) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$." + property);
    }
}
