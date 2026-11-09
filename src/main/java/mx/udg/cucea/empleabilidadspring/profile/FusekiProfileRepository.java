package mx.udg.cucea.empleabilidadspring.profile;

import static mx.udg.cucea.empleabilidadspring.profile.UserProfile.CompetencyRanking;
import static mx.udg.cucea.empleabilidadspring.profile.UserProfile.Language;
import static mx.udg.cucea.empleabilidadspring.profile.UserProfile.ProfessionalContact;
import static mx.udg.cucea.empleabilidadspring.profile.UserProfile.ProfileEntry;
import static mx.udg.cucea.empleabilidadspring.profile.UserProfile.SocioemotionalFactor;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.springframework.stereotype.Repository;

import mx.udg.cucea.empleabilidadspring.graph.EmpleabilidadVocabulary;
import mx.udg.cucea.empleabilidadspring.graph.FusekiGraphClient;
import mx.udg.cucea.empleabilidadspring.graph.GraphMappingException;

@Repository
public class FusekiProfileRepository {

    private static final String RDF_NS = RDF.uri;
    private static final List<String> ROOT_RELATIONS = List.of(
            "tieneRanking", "dominaLenguaje", "tieneEducacion", "tieneExperienciaLaboral",
            "tieneHabilidad", "tieneHabilidadSuave", "tienePerfilSocioemocional", "conoceA"
    );
    private static final List<String> ROOT_PROPERTIES = List.of(
            "identificador", "nombre", "correoContacto", "telefonoContacto",
            "tieneRanking", "dominaLenguaje", "tieneEducacion", "tieneExperienciaLaboral",
            "tieneHabilidad", "tieneHabilidadSuave", "tienePerfilSocioemocional", "conoceA"
    );

    private final FusekiGraphClient graphClient;
    private final EmpleabilidadVocabulary vocabulary;

    public FusekiProfileRepository(FusekiGraphClient graphClient, EmpleabilidadVocabulary vocabulary) {
        this.graphClient = graphClient;
        this.vocabulary = vocabulary;
    }

    public Optional<UserProfile> findBySubject(String subject) {
        String candidateIri = candidateIri(subject);
        Model model = graphClient.construct(profileQuery(candidateIri));
        try {
            Resource candidate = model.createResource(candidateIri);
            if (!model.contains(candidate, RDF.type, vocabulary.resource("Candidato"))) {
                return Optional.empty();
            }
            return Optional.of(mapProfile(model, candidate));
        } finally {
            model.close();
        }
    }

    public void replace(String subject, UserProfile profile) {
        validateReferences(profile);
        String candidateIri = candidateIri(subject);
        Model model = createProfileModel(candidateIri, profile);
        try {
            StringWriter triples = new StringWriter();
            RDFDataMgr.write(triples, model, Lang.NTRIPLES);
            String sparql = deleteProfileUpdate(candidateIri)
                    + "; INSERT DATA { " + triples + " }";
            graphClient.update(sparql);
        } finally {
            model.close();
        }
    }

    public void delete(String subject) {
        graphClient.update(deleteProfileUpdate(candidateIri(subject)));
    }

    static String candidateIri(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Authenticated Google subject must not be blank.");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(subject.getBytes(StandardCharsets.UTF_8));
            return EmpleabilidadVocabulary.NS + "profile/" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required to derive the opaque profile identifier.", exception);
        }
    }

    private void validateReferences(UserProfile profile) {
        for (CompetencyRanking ranking : profile.competencies()) {
            ParameterizedSparqlString query = new ParameterizedSparqlString("""
                    PREFIX emp: <%s>
                    ASK { ?competency a emp:Competencia }
                    """.formatted(EmpleabilidadVocabulary.NS));
            query.setIri("competency", ranking.competencyIri());
            if (!graphClient.ask(query.toString())) {
                throw new ProfileValidationException("A selected competency does not exist in the graph.");
            }
        }
        for (Language language : profile.languages()) {
            ParameterizedSparqlString query = new ParameterizedSparqlString("""
                    PREFIX emp: <%s>
                    PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
                    ASK { ?level skos:inScheme emp:escalaMCER }
                    """.formatted(EmpleabilidadVocabulary.NS));
            query.setIri("level", language.levelIri());
            if (!graphClient.ask(query.toString())) {
                throw new ProfileValidationException("A selected language level is not a valid MCER concept.");
            }
        }
    }

    private UserProfile mapProfile(Model model, Resource candidate) {
        List<CompetencyRanking> competencies = linkedResources(model, candidate, "tieneRanking").stream()
                .map(ranking -> {
                    String scaleIri = requiredLiteral(model, ranking, "escala");
                    if (!scaleIri.equals(EmpleabilidadVocabulary.NS + "escalaCompetencia1a5")) {
                        throw new GraphMappingException(
                                "A stored profile competency ranking uses an unsupported scale."
                        );
                    }
                    int score = requiredInteger(model, ranking, "puntuacion");
                    if (score < 1 || score > 5) {
                        throw new GraphMappingException(
                                "A stored profile competency score must be between 1 and 5."
                        );
                    }
                    return new CompetencyRanking(
                            requiredResourceIri(model, ranking, "evaluaCompetencia"),
                            score
                    );
                })
                .toList();
        List<Language> languages = linkedResources(model, candidate, "dominaLenguaje").stream()
                .map(language -> new Language(
                        requiredLiteral(model, language, "nombre"),
                        requiredResourceIri(model, language, "nivelMCER")
                ))
                .toList();

        List<ProfileEntry> education = mapEntries(model, candidate, "tieneEducacion");
        List<ProfileEntry> workExperience = mapEntries(model, candidate, "tieneExperienciaLaboral");
        List<ProfileEntry> skills = mapEntries(model, candidate, "tieneHabilidad");
        List<ProfileEntry> softSkills = mapEntries(model, candidate, "tieneHabilidadSuave");

        List<SocioemotionalFactor> factors = linkedResources(model, candidate, "tienePerfilSocioemocional").stream()
                .flatMap(profile -> linkedResources(model, profile, "tieneFactorSocioemocional").stream())
                .map(factor -> new SocioemotionalFactor(
                        requiredLiteral(model, factor, "nombre"),
                        optionalLiteral(model, factor, "descripcion").orElse(null),
                        optionalLiteral(model, factor, "metodoEvaluacion").orElse(null)
                ))
                .toList();
        List<ProfessionalContact> contacts = linkedResources(model, candidate, "conoceA").stream()
                .map(contact -> new ProfessionalContact(
                        requiredLiteral(model, contact, "nombre"),
                        optionalLiteral(model, contact, "descripcion").orElse(null)
                ))
                .toList();

        return new UserProfile(
                requiredLiteral(model, candidate, "nombre"),
                requiredLiteral(model, candidate, "correoContacto"),
                optionalLiteral(model, candidate, "telefonoContacto").orElse(null),
                competencies,
                languages,
                education,
                workExperience,
                skills,
                softSkills,
                factors,
                contacts
        );
    }

    private List<ProfileEntry> mapEntries(Model model, Resource candidate, String relation) {
        return linkedResources(model, candidate, relation).stream()
                .map(entry -> new ProfileEntry(
                        requiredLiteral(model, entry, "nombre"),
                        optionalLiteral(model, entry, "descripcion").orElse(null),
                        optionalDate(model, entry, "fechaInicio"),
                        optionalDate(model, entry, "fechaFin")
                ))
                .toList();
    }

    private Model createProfileModel(String candidateIri, UserProfile profile) {
        Model model = org.apache.jena.rdf.model.ModelFactory.createDefaultModel();
        Resource candidate = model.createResource(candidateIri);
        model.add(candidate, RDF.type, vocabulary.resource("Candidato"));
        model.add(candidate, vocabulary.property("identificador"), candidateIri.substring(candidateIri.lastIndexOf('/') + 1));
        model.add(candidate, vocabulary.property("nombre"), profile.name());
        model.add(candidate, vocabulary.property("correoContacto"), profile.contactEmail());
        addLiteralIfPresent(model, candidate, "telefonoContacto", profile.contactPhone());

        profile.competencies().forEach(ranking -> {
            Resource node = ownedNode(model, candidateIri, "ranking");
            model.add(candidate, vocabulary.property("tieneRanking"), node);
            model.add(node, RDF.type, vocabulary.resource("RankingDeCompetencia"));
            model.add(node, vocabulary.property("evaluaCompetencia"), model.createResource(ranking.competencyIri()));
            model.addLiteral(node, vocabulary.property("puntuacion"), ranking.score());
            model.add(node, vocabulary.property("escala"),
                    model.createTypedLiteral(EmpleabilidadVocabulary.NS + "escalaCompetencia1a5", XSDDatatype.XSDanyURI));
            model.add(node, vocabulary.property("metodoEvaluacion"), "Declaración del usuario");
        });
        profile.languages().forEach(language -> {
            Resource node = ownedNode(model, candidateIri, "language");
            model.add(candidate, vocabulary.property("dominaLenguaje"), node);
            model.add(node, RDF.type, vocabulary.resource("Lenguaje"));
            model.add(node, vocabulary.property("nombre"), language.name());
            model.add(node, vocabulary.property("nivelMCER"), model.createResource(language.levelIri()));
        });
        addEntries(model, candidateIri, candidate, "tieneEducacion", "Educacion", "education", profile.education());
        addEntries(model, candidateIri, candidate, "tieneExperienciaLaboral", "ExperienciaLaboral",
                "experience", profile.workExperience());
        addEntries(model, candidateIri, candidate, "tieneHabilidad", "Habilidad", "skill", profile.skills());
        addEntries(model, candidateIri, candidate, "tieneHabilidadSuave", "HabilidadSuave",
                "soft-skill", profile.softSkills());

        if (!profile.socioemotionalFactors().isEmpty()) {
            Resource socioemotionalProfile = ownedNode(model, candidateIri, "socioemotional-profile");
            model.add(candidate, vocabulary.property("tienePerfilSocioemocional"), socioemotionalProfile);
            model.add(socioemotionalProfile, RDF.type, vocabulary.resource("PerfilSocioemocional"));
            model.add(socioemotionalProfile, vocabulary.property("nombre"), "Perfil socioemocional declarado");
            profile.socioemotionalFactors().forEach(factor -> {
                Resource node = ownedNode(model, candidateIri, "socioemotional-factor");
                model.add(socioemotionalProfile, vocabulary.property("tieneFactorSocioemocional"), node);
                model.add(node, RDF.type, vocabulary.resource("FactorSocioemocional"));
                model.add(node, vocabulary.property("nombre"), factor.name());
                addLiteralIfPresent(model, node, "descripcion", factor.description());
                addLiteralIfPresent(model, node, "metodoEvaluacion", factor.evaluationMethod());
            });
        }
        profile.professionalContacts().forEach(contact -> {
            Resource node = ownedNode(model, candidateIri, "professional-contact");
            model.add(candidate, vocabulary.property("conoceA"), node);
            model.add(node, RDF.type, vocabulary.resource("RedSocial"));
            model.add(node, vocabulary.property("nombre"), contact.name());
            addLiteralIfPresent(model, node, "descripcion", contact.description());
        });
        return model;
    }

    private void addEntries(
            Model model,
            String candidateIri,
            Resource candidate,
            String relation,
            String type,
            String nodeKind,
            List<ProfileEntry> entries
    ) {
        entries.forEach(entry -> {
            Resource node = ownedNode(model, candidateIri, nodeKind);
            model.add(candidate, vocabulary.property(relation), node);
            model.add(node, RDF.type, vocabulary.resource(type));
            model.add(node, vocabulary.property("nombre"), entry.name());
            addLiteralIfPresent(model, node, "descripcion", entry.description());
            addDateIfPresent(model, node, "fechaInicio", entry.startDate());
            addDateIfPresent(model, node, "fechaFin", entry.endDate());
        });
    }

    private Resource ownedNode(Model model, String candidateIri, String type) {
        return model.createResource(candidateIri + "/" + type + "/" + UUID.randomUUID());
    }

    private void addLiteralIfPresent(Model model, Resource subject, String property, String value) {
        if (value != null) {
            model.add(subject, vocabulary.property(property), value);
        }
    }

    private void addDateIfPresent(Model model, Resource subject, String property, LocalDate value) {
        if (value != null) {
            model.add(subject, vocabulary.property(property), model.createTypedLiteral(value.toString(), XSDDatatype.XSDdate));
        }
    }

    private String profileQuery(String candidateIri) {
        ParameterizedSparqlString query = new ParameterizedSparqlString("""
                PREFIX emp: <%s>
                PREFIX rdf: <%s>
                CONSTRUCT {
                    ?candidate ?p ?o .
                    ?child ?cp ?co .
                    ?factor ?fp ?fo .
                }
                WHERE {
                    VALUES ?candidate { ?candidateIri }
                    ?candidate ?p ?o .
                    FILTER(?p IN (rdf:type, emp:identificador, emp:nombre, emp:correoContacto, emp:telefonoContacto,
                        emp:tieneRanking, emp:dominaLenguaje, emp:tieneEducacion, emp:tieneExperienciaLaboral,
                        emp:tieneHabilidad, emp:tieneHabilidadSuave, emp:tienePerfilSocioemocional, emp:conoceA))
                    OPTIONAL {
                        ?candidate ?relation ?child .
                        FILTER(?relation IN (emp:tieneRanking, emp:dominaLenguaje, emp:tieneEducacion,
                            emp:tieneExperienciaLaboral, emp:tieneHabilidad, emp:tieneHabilidadSuave,
                            emp:tienePerfilSocioemocional, emp:conoceA))
                        ?child ?cp ?co .
                        OPTIONAL {
                            ?child emp:tieneFactorSocioemocional ?factor .
                            ?factor ?fp ?fo .
                        }
                    }
                }
                """.formatted(EmpleabilidadVocabulary.NS, RDF_NS));
        query.setIri("candidateIri", candidateIri);
        return query.toString();
    }

    private String deleteProfileUpdate(String candidateIri) {
        String propertyList = ROOT_PROPERTIES.stream()
                .map(name -> "<" + EmpleabilidadVocabulary.NS + name + ">")
                .collect(java.util.stream.Collectors.joining(", "));
        String ownedPrefix = candidateIri + "/";
        return """
                DELETE { ?owned ?childPredicate ?childValue }
                WHERE {
                    ?owned ?childPredicate ?childValue .
                    FILTER(isIRI(?owned) && STRSTARTS(STR(?owned), "%s"))
                };
                DELETE { <%s> ?profilePredicate ?profileValue }
                WHERE {
                    <%s> ?profilePredicate ?profileValue .
                    FILTER(?profilePredicate IN (<%s>, <%s>, %s))
                }
                """.formatted(
                ownedPrefix, candidateIri, candidateIri,
                RDF.type.getURI(),
                EmpleabilidadVocabulary.NS + "identificador",
                propertyList
        );
    }

    private List<Resource> linkedResources(Model model, Resource subject, String relation) {
        List<Resource> resources = new ArrayList<>();
        model.listObjectsOfProperty(subject, vocabulary.property(relation)).forEachRemaining(value -> {
            if (!value.isResource() || !value.asResource().isURIResource()) {
                throw new GraphMappingException("Profile relation emp:" + relation + " contains an invalid node.");
            }
            resources.add(value.asResource());
        });
        return resources.stream().distinct().sorted(Comparator.comparing(Resource::getURI)).toList();
    }

    private String requiredLiteral(Model model, Resource subject, String property) {
        return optionalLiteral(model, subject, property).orElseThrow(() ->
                new GraphMappingException("Profile graph is missing required emp:" + property + "."));
    }

    private Optional<String> optionalLiteral(Model model, Resource subject, String property) {
        List<RDFNode> values = model.listObjectsOfProperty(subject, vocabulary.property(property)).toList();
        if (values.size() > 1 || values.stream().anyMatch(value -> !value.isLiteral())) {
            throw new GraphMappingException("Profile graph has an invalid value for emp:" + property + ".");
        }
        return values.stream().findFirst().map(value -> value.asLiteral().getLexicalForm());
    }

    private String requiredResourceIri(Model model, Resource subject, String property) {
        List<RDFNode> values = model.listObjectsOfProperty(subject, vocabulary.property(property)).toList();
        if (values.size() != 1 || !values.get(0).isURIResource()) {
            throw new GraphMappingException("Profile graph has an invalid resource for emp:" + property + ".");
        }
        return values.get(0).asResource().getURI();
    }

    private int requiredInteger(Model model, Resource subject, String property) {
        Optional<Integer> value = optionalInteger(model, subject, property);
        if (value.isEmpty()) {
            throw new GraphMappingException("Profile graph is missing required emp:" + property + ".");
        }
        return value.get();
    }

    private Optional<Integer> optionalInteger(Model model, Resource subject, String property) {
        Optional<String> value = optionalLiteral(model, subject, property);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(value.get()));
        } catch (NumberFormatException exception) {
            throw new GraphMappingException("Profile graph has a non-integer emp:" + property + ".", exception);
        }
    }

    private LocalDate optionalDate(Model model, Resource subject, String property) {
        Optional<String> value = optionalLiteral(model, subject, property);
        if (value.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value.get());
        } catch (DateTimeParseException exception) {
            throw new GraphMappingException("Profile graph has an invalid date for emp:" + property + ".", exception);
        }
    }
}
