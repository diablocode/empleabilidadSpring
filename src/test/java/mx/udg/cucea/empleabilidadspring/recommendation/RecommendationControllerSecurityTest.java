package mx.udg.cucea.empleabilidadspring.recommendation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import mx.udg.cucea.empleabilidadspring.auth.AuthenticatedUser;
import mx.udg.cucea.empleabilidadspring.graph.FusekiGraphException;
import mx.udg.cucea.empleabilidadspring.recommendation.RecommendationResponse.Status;

@SpringBootTest(properties = "app.google.client-id=test-google-client-id")
@AutoConfigureMockMvc
class RecommendationControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecommendationService recommendationService;

    @Test
    void recommendationsRequireAnAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/recommendations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"));
    }

    @Test
    void reportsIncompleteProfileSeparately() throws Exception {
        AuthenticatedUser user = user();
        when(recommendationService.getRecommendations(user)).thenReturn(new RecommendationResponse(
                Status.INCOMPLETE_PROFILE, List.of(), List.of("competencies")
        ));

        mockMvc.perform(get("/api/recommendations").session(session(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INCOMPLETE_PROFILE"))
                .andExpect(jsonPath("$.recommendations").isEmpty())
                .andExpect(jsonPath("$.missingProfileFields[0]").value("competencies"));
    }

    @Test
    void reportsNoMatchesSeparatelyFromAnIncompleteProfile() throws Exception {
        AuthenticatedUser user = user();
        when(recommendationService.getRecommendations(user)).thenReturn(new RecommendationResponse(
                Status.NO_MATCHES, List.of(), List.of()
        ));

        mockMvc.perform(get("/api/recommendations").session(session(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_MATCHES"))
                .andExpect(jsonPath("$.recommendations").isEmpty())
                .andExpect(jsonPath("$.missingProfileFields").isEmpty());
    }

    @Test
    void reportsFusekiFailureAsAServiceUnavailableRecommendationError() throws Exception {
        AuthenticatedUser user = user();
        when(recommendationService.getRecommendations(user)).thenThrow(
                new RecommendationService.DataUnavailableException(
                        new FusekiGraphException("Fuseki unavailable.", new RuntimeException("offline"))
                )
        );

        mockMvc.perform(get("/api/recommendations").session(session(user)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("recommendation_data_unavailable"));

        verify(recommendationService).getRecommendations(user);
    }

    private static AuthenticatedUser user() {
        return new AuthenticatedUser("google-subject", "user@example.com", "Example User", null);
    }

    private static MockHttpSession session(AuthenticatedUser user) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                user, null, List.of(() -> "ROLE_USER")));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
}
