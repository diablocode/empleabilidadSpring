package mx.udg.cucea.empleabilidadspring.profile;

import static mx.udg.cucea.empleabilidadspring.profile.UserProfile.*;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;

import mx.udg.cucea.empleabilidadspring.auth.AuthenticatedUser;

@Service
public class ProfileService {

    private static final int MAX_ITEMS = 100;

    private final FusekiProfileRepository repository;

    public ProfileService(FusekiProfileRepository repository) {
        this.repository = repository;
    }

    public UserProfile get(AuthenticatedUser user) {
        return repository.findBySubject(user.subject())
                .orElseThrow(ProfileNotFoundException::new);
    }

    public UserProfile save(AuthenticatedUser user, UserProfile profile) {
        validate(profile);
        repository.replace(user.subject(), profile);
        return profile;
    }

    public void delete(AuthenticatedUser user) {
        repository.delete(user.subject());
    }

    private void validate(UserProfile profile) {
        if (profile == null) {
            throw invalid("A profile body is required.");
        }
        required(profile.name(), "name", 150);
        required(profile.contactEmail(), "contactEmail", 254);
        if (!profile.contactEmail().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw invalid("contactEmail must be a valid email address.");
        }
        optional(profile.contactPhone(), "contactPhone", 40);
        if (profile.contactPhone() != null && !profile.contactPhone().matches("(?=.*\\d)[+0-9(). -]+")) {
            throw invalid("contactPhone contains unsupported characters.");
        }

        bounded(profile.competencies(), "competencies");
        Set<String> competencyIris = new HashSet<>();
        for (CompetencyRanking item : profile.competencies()) {
            if (item == null || item.competencyIri() == null || item.competencyIri().isBlank()) {
                throw invalid("Each competency must reference a graph competency.");
            }
            if (item.score() < 1 || item.score() > 5) {
                throw invalid("Competency scores must be between 1 and 5.");
            }
            requireHttpIri(item.competencyIri(), "competencyIri");
            if (!competencyIris.add(item.competencyIri())) {
                throw invalid("A competency may only be ranked once.");
            }
        }

        bounded(profile.languages(), "languages");
        Set<String> languageNames = new HashSet<>();
        for (Language item : profile.languages()) {
            if (item == null) {
                throw invalid("Each language entry must be an object.");
            }
            required(item.name(), "language name", 100);
            required(item.levelIri(), "language level", 2048);
            requireHttpIri(item.levelIri(), "language level");
            if (!languageNames.add(item.name().strip().toLowerCase(Locale.ROOT))) {
                throw invalid("A language may only be listed once.");
            }
        }

        validateEntries(profile.education(), "education");
        validateEntries(profile.workExperience(), "workExperience");
        validateEntries(profile.skills(), "skills");
        validateEntries(profile.softSkills(), "softSkills");

        bounded(profile.socioemotionalFactors(), "socioemotionalFactors");
        for (SocioemotionalFactor factor : profile.socioemotionalFactors()) {
            if (factor == null) {
                throw invalid("Each socioemotional factor must be an object.");
            }
            required(factor.name(), "socioemotional factor name", 150);
            optional(factor.description(), "socioemotional factor description", 2000);
            optional(factor.evaluationMethod(), "socioemotional evaluation method", 200);
        }

        bounded(profile.professionalContacts(), "professionalContacts");
        for (ProfessionalContact contact : profile.professionalContacts()) {
            if (contact == null) {
                throw invalid("Each professional contact must be an object.");
            }
            required(contact.name(), "professional contact name", 150);
            optional(contact.description(), "professional contact description", 2000);
        }
    }

    private void validateEntries(List<ProfileEntry> entries, String field) {
        bounded(entries, field);
        for (ProfileEntry entry : entries) {
            if (entry == null) {
                throw invalid("Each " + field + " entry must be an object.");
            }
            required(entry.name(), field + " name", 150);
            optional(entry.description(), field + " description", 2000);
            if (entry.startDate() != null && entry.endDate() != null && entry.endDate().isBefore(entry.startDate())) {
                throw invalid(field + " endDate must not be before startDate.");
            }
        }
    }

    private void bounded(List<?> values, String field) {
        if (values.size() > MAX_ITEMS) {
            throw invalid(field + " may contain at most " + MAX_ITEMS + " entries.");
        }
    }

    private void required(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw invalid(field + " is required and must be at most " + maxLength + " characters.");
        }
    }

    private void optional(String value, String field, int maxLength) {
        if (value != null && (value.length() > maxLength || value.isBlank())) {
            throw invalid(field + " must be omitted or contain at most " + maxLength + " characters.");
        }
    }

    private void requireHttpIri(String value, String field) {
        try {
            if (value.length() > 2048) {
                throw invalid(field + " must be an absolute HTTP(S) IRI.");
            }
            URI iri = URI.create(value);
            if (!iri.isAbsolute() || iri.getHost() == null
                    || !("http".equalsIgnoreCase(iri.getScheme()) || "https".equalsIgnoreCase(iri.getScheme()))) {
                throw invalid(field + " must be an absolute HTTP(S) IRI.");
            }
        } catch (IllegalArgumentException exception) {
            throw invalid(field + " must be an absolute HTTP(S) IRI.");
        }
    }

    private ProfileValidationException invalid(String message) {
        return new ProfileValidationException(message);
    }
}
