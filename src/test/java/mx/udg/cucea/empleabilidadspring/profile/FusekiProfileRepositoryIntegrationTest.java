package mx.udg.cucea.empleabilidadspring.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import mx.udg.cucea.empleabilidadspring.graph.EmpleabilidadVocabulary;
import mx.udg.cucea.empleabilidadspring.graph.FusekiOfferRepository;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "FUSEKI_INTEGRATION", matches = "true")
class FusekiProfileRepositoryIntegrationTest {

    @Autowired
    private FusekiProfileRepository profileRepository;

    @Autowired
    private FusekiOfferRepository offerRepository;

    @Test
    void profileRoundTripsInFusekiWithoutChangingSourceOffersOrExposingItToAnotherUser() {
        String subject = "profile-integration-" + UUID.randomUUID();
        String otherSubject = "profile-integration-other-" + UUID.randomUUID();
        List<?> sourceOffers = offerRepository.findPage(0, 20);
        UserProfile profile = new UserProfile(
                "Integration Candidate",
                "integration@example.com",
                "+52 33 1234 5678",
                List.of(),
                List.of(new UserProfile.Language("English", EmpleabilidadVocabulary.NS + "nivelB2")),
                List.of(new UserProfile.ProfileEntry(
                        "Engineering degree", "University program",
                        LocalDate.of(2018, 8, 1), LocalDate.of(2022, 6, 30))),
                List.of(new UserProfile.ProfileEntry(
                        "Developer", "Backend work",
                        LocalDate.of(2022, 7, 1), null)),
                List.of(new UserProfile.ProfileEntry("Java", null, null, null)),
                List.of(new UserProfile.ProfileEntry("Collaboration", null, null, null)),
                List.of(new UserProfile.SocioemotionalFactor(
                        "Communication", "Feedback from project teams", "Self-assessment")),
                List.of(new UserProfile.ProfessionalContact("Former colleagues", "Professional group"))
        );

        try {
            assertTrue(profileRepository.findBySubject(subject).isEmpty());
            profileRepository.replace(subject, profile);
            assertEquals(profile, profileRepository.findBySubject(subject).orElseThrow());
            assertTrue(profileRepository.findBySubject(otherSubject).isEmpty());
            assertEquals(sourceOffers, offerRepository.findPage(0, 20));
        } finally {
            profileRepository.delete(subject);
            profileRepository.delete(otherSubject);
        }

        assertTrue(profileRepository.findBySubject(subject).isEmpty());
        assertEquals(sourceOffers, offerRepository.findPage(0, 20));
    }
}
