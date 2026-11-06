package mx.udg.cucea.empleabilidadspring.profile;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import mx.udg.cucea.empleabilidadspring.auth.AuthenticatedUser;

@SpringBootTest(properties = "app.google.client-id=test-google-client-id")
@AutoConfigureMockMvc
class ProfileControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FusekiProfileRepository repository;

    @Test
    void profileRoutesRequireAnAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"));
    }

    @Test
    void profileReadsAndDeletesAreScopedToTheAuthenticatedGoogleSubject() throws Exception {
        AuthenticatedUser firstUser = user("google-subject-one");
        UserProfile profile = new UserProfile(
                "First User", "first@example.com", null,
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of()
        );
        when(repository.findBySubject(firstUser.subject())).thenReturn(Optional.of(profile));

        mockMvc.perform(get("/api/profile").session(session(firstUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("First User"));
        MockHttpSession session = session(firstUser);
        CsrfToken csrf = csrf(session);
        mockMvc.perform(delete("/api/profile")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token()))
                .andExpect(status().isNoContent());

        verify(repository).findBySubject(firstUser.subject());
        verify(repository).delete(firstUser.subject());
    }

    @Test
    void putRequiresCsrfAndRejectsInvalidProfileValuesBeforeWriting() throws Exception {
        MockHttpSession session = session(user("google-subject-two"));
        String invalidProfile = """
                {
                  "name": "Second User",
                  "contactEmail": "not-an-email",
                  "competencies": [],
                  "languages": [],
                  "education": [],
                  "workExperience": [],
                  "skills": [],
                  "softSkills": [],
                  "socioemotionalFactors": [],
                  "professionalContacts": []
                }
                """;

        mockMvc.perform(put("/api/profile")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidProfile))
                .andExpect(status().isForbidden());

        CsrfToken csrf = csrf(session);
        mockMvc.perform(put("/api/profile")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidProfile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_profile"));
    }

    @Test
    void putSavesOnlyForTheAuthenticatedSubject() throws Exception {
        String payload = """
                {
                  "name": "Second User",
                  "contactEmail": "second@example.com",
                  "competencies": [],
                  "languages": [],
                  "education": [],
                  "workExperience": [],
                  "skills": [],
                  "softSkills": [],
                  "socioemotionalFactors": [],
                  "professionalContacts": []
                }
                """;
        AuthenticatedUser user = user("google-subject-two");
        MockHttpSession session = session(user);
        CsrfToken csrf = csrf(session);

        mockMvc.perform(put("/api/profile")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactEmail").value("second@example.com"));

        verify(repository).replace(user.subject(), new UserProfile(
                "Second User", "second@example.com", null,
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of()
        ));
    }

    @Test
    void reportsGraphWriteFailuresAsServiceUnavailable() throws Exception {
        AuthenticatedUser user = user("google-subject-three");
        MockHttpSession session = session(user);
        CsrfToken csrf = csrf(session);
        org.mockito.Mockito.doThrow(new mx.udg.cucea.empleabilidadspring.graph.FusekiGraphException(
                        "Fuseki graph update failed.", new RuntimeException("offline")))
                .when(repository)
                .replace(org.mockito.ArgumentMatchers.eq(user.subject()), org.mockito.ArgumentMatchers.any());
        String payload = """
                {
                  "name": "Third User",
                  "contactEmail": "third@example.com"
                }
                """;

        mockMvc.perform(put("/api/profile")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("profile_store_unavailable"));
    }

    private static AuthenticatedUser user(String subject) {
        return new AuthenticatedUser(subject, "user@example.com", "Example User", null);
    }

    private static MockHttpSession session(AuthenticatedUser user) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                user, null, java.util.List.of(() -> "ROLE_USER")));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private CsrfToken csrf(MockHttpSession session) throws Exception {
        org.springframework.test.web.servlet.MvcResult result = mockMvc.perform(get("/api/auth/csrf")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn();
        return new CsrfToken(
                new MockCookie("XSRF-TOKEN", result.getResponse().getCookie("XSRF-TOKEN").getValue()),
                com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.token")
        );
    }

    private record CsrfToken(MockCookie cookie, String token) {
    }
}
