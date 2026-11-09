package mx.udg.cucea.empleabilidadspring.opportunity;

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

@SpringBootTest(properties = "app.google.client-id=test-google-client-id")
@AutoConfigureMockMvc
class CompetencyControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CompetencyService competencyService;

    @Test
    void catalogRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/competencies"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"));
    }

    @Test
    void catalogReturnsOpaqueIdsAndNames() throws Exception {
        when(competencyService.list(0, 100)).thenReturn(new CompetencyResponse(
                0,
                100,
                1,
                false,
                List.of(new CompetencyResponse.Competency("public-competency-id", "Java"))
        ));

        mockMvc.perform(get("/api/competencies").session(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.competencies[0].competencyId").value("public-competency-id"))
                .andExpect(jsonPath("$.competencies[0].name").value("Java"))
                .andExpect(jsonPath("$.competencies[0].iri").doesNotExist());
    }

    private static MockHttpSession session() {
        AuthenticatedUser user = new AuthenticatedUser("google-subject", "user@example.com", "Candidate", null);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                user, null, List.of(() -> "ROLE_USER")));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
}
