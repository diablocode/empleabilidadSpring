package mx.udg.cucea.empleabilidadspring.profile;

import java.util.List;

import mx.udg.cucea.empleabilidadspring.graph.EmpleabilidadVocabulary;
import mx.udg.cucea.empleabilidadspring.opportunity.PublicResourceIds;

public final class ProfileApiModel {

    private ProfileApiModel() {
    }

    public record Request(
            String name,
            String contactEmail,
            String contactPhone,
            List<Competency> competencies,
            List<Language> languages,
            List<UserProfile.ProfileEntry> education,
            List<UserProfile.ProfileEntry> workExperience,
            List<UserProfile.ProfileEntry> skills,
            List<UserProfile.ProfileEntry> softSkills,
            List<UserProfile.SocioemotionalFactor> socioemotionalFactors,
            List<UserProfile.ProfessionalContact> professionalContacts
    ) {
        UserProfile toDomain() {
            return new UserProfile(
                    name,
                    contactEmail,
                    contactPhone,
                    competencies == null ? List.of() : competencies.stream()
                            .map(item -> item == null
                                    ? null
                                    : new UserProfile.CompetencyRanking(item.competencyId(), item.score()))
                            .toList(),
                    languages == null ? List.of() : languages.stream()
                            .map(item -> item == null ? null : new UserProfile.Language(
                                    item.name(), languageIri(item.level())
                            ))
                            .toList(),
                    education,
                    workExperience,
                    skills,
                    softSkills,
                    socioemotionalFactors,
                    professionalContacts
            );
        }
    }

    public record Response(
            String name,
            String contactEmail,
            String contactPhone,
            List<Competency> competencies,
            List<Language> languages,
            List<UserProfile.ProfileEntry> education,
            List<UserProfile.ProfileEntry> workExperience,
            List<UserProfile.ProfileEntry> skills,
            List<UserProfile.ProfileEntry> softSkills,
            List<UserProfile.SocioemotionalFactor> socioemotionalFactors,
            List<UserProfile.ProfessionalContact> professionalContacts
    ) {
        static Response from(UserProfile profile) {
            return new Response(
                    profile.name(),
                    profile.contactEmail(),
                    profile.contactPhone(),
                    profile.competencies().stream()
                            .map(item -> new Competency(
                                    PublicResourceIds.fromIri(item.competencyIri()),
                                    item.score()
                            ))
                            .toList(),
                    profile.languages().stream()
                            .map(item -> new Language(item.name(), languageLevel(item.levelIri())))
                            .toList(),
                    profile.education(),
                    profile.workExperience(),
                    profile.skills(),
                    profile.softSkills(),
                    profile.socioemotionalFactors(),
                    profile.professionalContacts()
            );
        }
    }

    public record Competency(String competencyId, int score) {
    }

    public record Language(String name, String level) {
    }

    private static String languageIri(String level) {
        if (level == null || !List.of("A1", "A2", "B1", "B2", "C1", "C2").contains(level)) {
            throw new ProfileValidationException("Language level must be one of A1, A2, B1, B2, C1, or C2.");
        }
        return EmpleabilidadVocabulary.NS + "nivel" + level;
    }

    private static String languageLevel(String levelIri) {
        String prefix = EmpleabilidadVocabulary.NS + "nivel";
        if (levelIri.startsWith(prefix)) {
            return levelIri.substring(prefix.length());
        }
        throw new ProfileValidationException("A stored profile language uses an unsupported level.");
    }
}
