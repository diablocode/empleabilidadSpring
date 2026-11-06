package mx.udg.cucea.empleabilidadspring.profile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import mx.udg.cucea.empleabilidadspring.graph.EmpleabilidadVocabulary;
import mx.udg.cucea.empleabilidadspring.graph.FusekiGraphClient;

@ExtendWith(MockitoExtension.class)
class FusekiProfileRepositoryTest {

    @Mock
    private FusekiGraphClient graphClient;

    private FusekiProfileRepository repository;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        repository = new FusekiProfileRepository(graphClient, new EmpleabilidadVocabulary());
    }

    @Test
    void derivesAnOpaqueCandidateIriWithoutEmbeddingTheGoogleSubject() {
        String subject = "google-subject-private";
        String candidateIri = FusekiProfileRepository.candidateIri(subject);

        assertTrue(candidateIri.startsWith(EmpleabilidadVocabulary.NS + "profile/"));
        assertFalse(candidateIri.contains(subject));
        assertTrue(candidateIri.matches(".*#profile/[0-9a-f]{64}$"));
    }

    @Test
    void writesOnlyTheCurrentUsersProfileVocabularyAndValidatesGraphReferences() {
        when(graphClient.ask(anyString())).thenReturn(true);
        UserProfile profile = new UserProfile(
                "Example Person",
                "person@example.com",
                null,
                List.of(new UserProfile.CompetencyRanking(EmpleabilidadVocabulary.NS + "competency-1", 4)),
                List.of(new UserProfile.Language("English", EmpleabilidadVocabulary.NS + "nivelB2")),
                List.of(new UserProfile.ProfileEntry("Degree", null, LocalDate.of(2020, 1, 1), null)),
                List.of(),
                List.of(),
                List.of(),
                List.of(new UserProfile.SocioemotionalFactor("Communication", "Team feedback", "Self-assessment")),
                List.of(new UserProfile.ProfessionalContact("Mentor group", "Former colleagues"))
        );

        repository.replace("google-subject-private", profile);

        ArgumentCaptor<String> updates = ArgumentCaptor.forClass(String.class);
        verify(graphClient).update(updates.capture());
        String update = updates.getValue();
        assertTrue(update.contains("INSERT DATA"));
        assertTrue(update.contains("Example Person"));
        assertTrue(update.contains("Communication"));
        assertTrue(update.contains("Mentor group"));
        assertFalse(update.contains("google-subject-private"));
        assertFalse(update.contains("OfertaLaboral"));
        assertFalse(update.contains("publicadoPor"));
        assertTrue(update.contains(EmpleabilidadVocabulary.NS + "profile/"));
        verify(graphClient, org.mockito.Mockito.times(2)).ask(anyString());
    }

    @Test
    void profileReadUsesOnlyTheHashedUserResource() {
        when(graphClient.construct(anyString())).thenReturn(ModelFactory.createDefaultModel());

        repository.findBySubject("google-subject-private");

        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(graphClient).construct(query.capture());
        assertFalse(query.getValue().contains("google-subject-private"));
        assertTrue(query.getValue().contains("profile/"));
    }

    @Test
    void refusesUnknownVocabularyReferencesBeforeWritingAnyProfileTriples() {
        when(graphClient.ask(anyString())).thenReturn(false);
        UserProfile profile = new UserProfile(
                "Example Person",
                "person@example.com",
                null,
                List.of(new UserProfile.CompetencyRanking(EmpleabilidadVocabulary.NS + "unknown", 3)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        assertThrows(ProfileValidationException.class, () -> repository.replace("google-subject-private", profile));

        verify(graphClient, never()).update(anyString());
    }
}
