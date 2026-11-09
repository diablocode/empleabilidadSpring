package mx.udg.cucea.empleabilidadspring.opportunity;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import mx.udg.cucea.empleabilidadspring.auth.AuthenticatedUser;
import mx.udg.cucea.empleabilidadspring.opportunity.OpportunityResponse.Freshness;
import mx.udg.cucea.empleabilidadspring.opportunity.OpportunityResponse.Page;

@SpringBootTest(properties = "app.google.client-id=test-google-client-id")
@AutoConfigureMockMvc
class OpportunityControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OpportunityService opportunityService;

    @Test
    void listingRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/opportunities"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"));
    }

    @Test
    void listingReturnsDocumentedPageAndOpportunityFieldsWithoutRdfIdentifiers() throws Exception {
        AuthenticatedUser user = user();
        OpportunityResponse opportunity = new OpportunityResponse(
                "4f3fe8c0-00b1-33d9-ae4c-c10d0212aa20",
                "Backend developer",
                "Example employer",
                "Build backend services",
                "Guadalajara",
                "Hybrid",
                null,
                Freshness.CURRENT,
                List.of(new OpportunityResponse.Requirement(
                        "Java", 4, "COMPETENCY_1_TO_5", true
                ))
        );
        when(opportunityService.list(0, 20, true)).thenReturn(
                new Page(0, 20, 1, false, List.of(opportunity))
        );

        mockMvc.perform(get("/api/opportunities")
                        .param("currentOnly", "true")
                        .session(session(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offset").value(0))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.opportunities[0].offerId").value(opportunity.offerId()))
                .andExpect(jsonPath("$.opportunities[0].freshness").value("CURRENT"))
                .andExpect(jsonPath("$.opportunities[0].requirements[0].expectedLevel").value(4))
                .andExpect(jsonPath("$.opportunities[0].iri").doesNotExist())
                .andExpect(jsonPath("$.opportunities[0].applicationUrl").doesNotExist());
    }

    @Test
    void detailReturnsNotFoundForAnUnknownValidIdAndBadRequestForAnInvalidId() throws Exception {
        AuthenticatedUser user = user();
        when(opportunityService.get("00000000-0000-0000-0000-000000000001"))
                .thenThrow(new OpportunityService.NotFoundException());
        when(opportunityService.get("not-a-uuid")).thenThrow(
                new OpportunityService.InvalidRequestException("offerId must be a valid opportunity ID.")
        );

        mockMvc.perform(get("/api/opportunities/00000000-0000-0000-0000-000000000001")
                        .session(session(user)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("opportunity_not_found"));

        mockMvc.perform(get("/api/opportunities/not-a-uuid").session(session(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"));
    }

    @Test
    void detailReturnsThePublicOpportunityShape() throws Exception {
        AuthenticatedUser user = user();
        OpportunityResponse detail = new OpportunityResponse(
                "4f3fe8c0-00b1-33d9-ae4c-c10d0212aa20",
                "Backend developer",
                "Example employer",
                "Build backend services",
                null,
                null,
                null,
                Freshness.EXPIRED,
                List.of()
        );
        when(opportunityService.get(detail.offerId())).thenReturn(detail);

        mockMvc.perform(get("/api/opportunities/" + detail.offerId()).session(session(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Backend developer"))
                .andExpect(jsonPath("$.freshness").value("EXPIRED"))
                .andExpect(jsonPath("$.iri").doesNotExist());
    }

    @Test
    void invalidPaginationAndFusekiErrorsUseTheApiErrorShape() throws Exception {
        when(opportunityService.list(0, 51, false)).thenThrow(
                new OpportunityService.InvalidRequestException("limit must be between 1 and 50.")
        );
        mockMvc.perform(get("/api/opportunities")
                        .param("offset", "not-a-number")
                        .session(session(user())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"));

        mockMvc.perform(get("/api/opportunities")
                        .param("limit", "51")
                        .session(session(user())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"));
    }

    @Test
    void graphFailuresReturnAnExplicitServiceUnavailableError() throws Exception {
        when(opportunityService.list(0, 20, false)).thenThrow(
                new OpportunityService.DataUnavailableException(new RuntimeException("offline"))
        );

        mockMvc.perform(get("/api/opportunities").session(session(user())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("opportunity_data_unavailable"));
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
