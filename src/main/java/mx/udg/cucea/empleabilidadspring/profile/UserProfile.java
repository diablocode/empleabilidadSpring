package mx.udg.cucea.empleabilidadspring.profile;

import java.time.LocalDate;
import java.util.List;

public record UserProfile(
        String name,
        String contactEmail,
        String contactPhone,
        List<CompetencyRanking> competencies,
        List<Language> languages,
        List<ProfileEntry> education,
        List<ProfileEntry> workExperience,
        List<ProfileEntry> skills,
        List<ProfileEntry> softSkills,
        List<SocioemotionalFactor> socioemotionalFactors,
        List<ProfessionalContact> professionalContacts
) {
    public UserProfile {
        competencies = immutableOrEmpty(competencies);
        languages = immutableOrEmpty(languages);
        education = immutableOrEmpty(education);
        workExperience = immutableOrEmpty(workExperience);
        skills = immutableOrEmpty(skills);
        softSkills = immutableOrEmpty(softSkills);
        socioemotionalFactors = immutableOrEmpty(socioemotionalFactors);
        professionalContacts = immutableOrEmpty(professionalContacts);
    }

    private static <T> List<T> immutableOrEmpty(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public record CompetencyRanking(String competencyIri, int score) {
    }

    public record Language(String name, String levelIri) {
    }

    public record ProfileEntry(String name, String description, LocalDate startDate, LocalDate endDate) {
    }

    public record SocioemotionalFactor(String name, String description, String evaluationMethod) {
    }

    public record ProfessionalContact(String name, String description) {
    }
}
