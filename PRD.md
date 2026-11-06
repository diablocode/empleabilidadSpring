# Product Requirements Document: Empleabilidad

**Status:** Draft  
**Product:** Empleabilidad  
**Document date:** 2026-11-06

## 1. Product summary

Empleabilidad is a web application that helps users discover current job opportunities relevant to their professional profiles. Users authenticate with Google, create and maintain a profile, and receive ranked `OfertaLaboral` results and recommendations.

The application will use a React frontend and a Spring Boot backend. Job and related graph data will be queried from a locally hosted Apache Jena Fuseki dataset using SPARQL. The repository includes ontology version 1.1.0 in `src/main/resources/ontology/empleabilidad.ttl` and its RDF/XML serialization in `src/main/resources/ontology/empleabilidad.rdf`. This version adds dedicated candidate contact-email and optional telephone properties to the existing vocabulary and has been synchronized to the live Fuseki dataset.

## 2. Problem and opportunity

People looking for work need a practical way to compare available opportunities with their skills, experience, and preferences. A searchable list alone can make it difficult to identify which current openings are the best fit. Empleabilidad aims to make relevant opportunities easier to find by presenting an understandable ranking and personalized recommendations.

## 3. Goals and success measures

### Goals

- Let a user sign in using a Google account.
- Let an authenticated user create, view, and update a personal employment profile.
- Retrieve job opportunities from Fuseki through the Spring Boot application using SPARQL.
- Rank current opportunities against a user's profile and explain the main reasons for each match.
- Present recommendations in a clear, usable React interface.
- Use the Empleabilidad ontology v1.1.0 as the source vocabulary. The live dataset has been synchronized and validated against its SHACL shapes; candidate contact values still need to be supplied when users create profiles.

### Initial success measures

- A user can complete Google sign-in and reach their own profile.
- A user can save a profile and see it reflected in a subsequent session.
- The application displays opportunities retrieved from the configured Fuseki dataset and distinguishes current from non-current listings according to the graph's available data.
- For a profile with compatible data, results are ordered by a documented matching method and include understandable match reasons.
- The application gives a useful empty state when no matching/current opportunities are available; the current local Fuseki data is expected to produce this state until the user updates its expired offers.

Usage and recommendation quality metrics (for example, saved opportunities, click-throughs, or user feedback) can be selected after an initial deployment and baseline are available.

## 4. Users

- **Job seeker:** Signs in, builds a profile, reviews and compares ranked opportunities, and follows recommendations.
- **Application administrator/developer:** Configures the application and local Fuseki connection, inspects integration errors, and updates graph mappings when the dataset or ontology is known.

The first release is focused on individual job seekers. Administrative workflows for managing job listings or users are not part of the initial scope.

## 5. User stories

1. As a job seeker, I want to sign in with my Google account so that I can access my profile without creating a separate password.
2. As a job seeker, I want to create and update my profile so that recommendations can reflect my qualifications and preferences.
3. As a job seeker, I want to see current opportunities ranked for my profile so that I can focus on relevant openings.
4. As a job seeker, I want to understand why an opportunity is recommended so that I can judge whether the match is useful.
5. As a job seeker, I want to open an opportunity's available details so that I can decide what to do next.
6. As a job seeker, I want clear feedback when my profile is incomplete or there are no matching opportunities so that I know how to proceed.

## 6. Functional requirements

### Authentication and account access

- Use Google's official Google Identity Services sign-in button/client for authentication. The application provides a **Sign in with Google** button and delegates credential selection and authentication to Google's provided experience.
- Do not build a separate username/password login, password reset, credential entry form, or application-managed sign-up/authentication flow. The application must not collect or store Google passwords.
- After Google authentication, validate the returned Google identity credential on the backend and establish the application's authenticated session for profile and recommendation APIs.
- Require authentication for profile and personalized recommendation features.
- Associate each profile with the authenticated user's stable identity, not only a display name or email address.
- Provide sign-out and handle cancelled, expired, or failed Google authentication with clear feedback.
- Request only the identity information needed by the product. Any profile creation/editing is employment-profile functionality, not a separate authentication or account-registration flow.

### User profile

- Allow an authenticated user to create, view, and edit their own profile.
- Scope profile access so users cannot view or modify another user's profile.
- Indicate which profile information is needed to improve recommendations.
- Validate inputs and provide feedback for invalid or incomplete profile information.
- Use `emp:Candidato` as the candidate profile concept. The ontology supports linked education (`emp:tieneEducacion`), work experience (`emp:tieneExperienciaLaboral`), competencies (`emp:poseeCompetencia`), skills (`emp:tieneHabilidad`), soft skills (`emp:tieneHabilidadSuave`), languages (`emp:dominaLenguaje`), competence rankings (`emp:tieneRanking`), optional socioemotional profiles, and professional networks. Offer both socioemotional-profile and professional-network sections from the first release, but make them optional; neither may block profile completion or recommendations.
- Interpret `emp:RedSocial` as a real person or group the candidate knows professionally, not an electronic social-network service. Any details about those contacts must be limited to what is needed to represent a professional connection and related resources; do not assume the contact has an account in the application.
- Include the candidate's name and email in the profile in addition to the Google identity; phone is optional. Reuse existing `emp:nombre` for the candidate name and use `emp:correoContacto` and optional `emp:telefonoContacto` datatype properties added in ontology v1.1.0 and synchronized to Fuseki. Do not repurpose unrelated generic properties.
- Persist user profiles as RDF in the same Fuseki RDF graph as source job data, linked to the authenticated user's stable identity. Because profile and source triples share a graph, application updates must be restricted to the authenticated user's candidate resources and must not overwrite or delete source offer data.

### Opportunity retrieval and ranking

- The Spring Boot backend must query the configured Fuseki dataset through SPARQL; the browser must not connect directly to Fuseki.
- Retrieve `emp:OfertaLaboral` data and related graph data needed to display, filter, and rank opportunities. The ontology links an offer to an employer through `emp:publicadoPor`, and to competency requirements through `emp:requiereCompetencia` → `emp:especificaCompetencia`.
- Use `emp:nivelEsperado` and `emp:esObligatorio` as requirement-level signals when present. Treat an offer as non-current if its `emp:fechaFin` is in the past or its known `emp:estadoOferta` is `cerrada`, `expirada`, or `cubierta`. Other known status values may remain eligible. If `emp:fechaFin` is missing, require a known non-excluded status; if both the end date and status are missing, exclude the offer as indeterminate. Never treat missing/unknown status alone as evidence that an offer is current.
- Give matching competencies and mandatory competency levels priority in the initial ranking; other populated, approved signals are secondary. Optional socioemotional and professional-network profile data may influence recommendations when the user has supplied it. Explain all ranking signals used, do not infer protected traits, and never require these optional sections. Candidate competency rankings must be interpreted with their `emp:escala`; the ontology defines an ordinal 1–5 competency scheme, and scores from different scales must not be compared as if equivalent. Language levels use the controlled `emp:nivelMCER` concept scheme where present.
- Document the ranking inputs and ordering. Provide match explanations based on actual matching signals; do not imply certainty or guarantee employment.
- Represent recommendation results using `emp:Recomendacion` and `emp:Explicacion` where appropriate. Recommendation type can use the provided SKOS concepts, including `oferta-compatible`, `competencia-por-desarrollar`, `recurso-de-aprendizaje`, `mejora-de-perfil`, and `siguiente-accion-sugerida`. Link explanations to evidence with `emp:utilizaEvidencia`; record method version and recommendation validity when those fields are used.
- Handle missing graph properties and partially populated profiles without crashing. The behavior for each missing field must be specified during graph mapping and ranking design.
- Return enough opportunity details for a user to understand a result, using fields available in the graph. External application links, if present, should be clearly identified.
- Show an application link only when a valid destination is actually present in the graph; otherwise show offer details without inventing or configuring a substitute destination.
- Keep ranking computation and SPARQL construction on the backend, use parameterized/safely constructed query values, and validate user-supplied filters.

### User interface

- Build the frontend with React and provide responsive layouts for desktop and mobile browsers.
- Include sign-in, profile creation/editing, ranked results, and opportunity detail/summary views.
- Communicate loading, empty, and error states clearly.
- Make the most relevant match reasons visible without requiring users to understand SPARQL or RDF.
- Provide accessible labels, keyboard navigation, and sufficient visual contrast.

## 7. Main user flow

1. A visitor opens the application and selects Google's **Sign in with Google** button.
2. Google handles the authentication experience. The backend validates the returned identity credential, then the application checks whether the authenticated user has a profile.
3. A new user creates a profile; a returning user reviews or updates theirs.
4. The user opens recommendations. The backend obtains the relevant graph data from Fuseki, determines which opportunities are current, and ranks them against the profile.
5. The user reviews the ranked opportunities and their match explanations, then opens any available details or application link.
6. If the profile or dataset cannot support recommendations, the application explains what is missing and offers an appropriate next step.

## 8. Proposed technical boundaries

- **Frontend:** React single-page web application. Calls application APIs over HTTPS; does not hold client secrets or access Fuseki directly.
- **Backend:** Spring Boot application responsible for validating Google Identity Services credentials and establishing the application session, profile authorization and persistence, SPARQL access, ranking, and API responses.
- **Graph data:** Apache Jena Fuseki 6.2.0 is running locally at `http://localhost:3030`, with dataset `/empleabilidad`. The ontology namespace is `https://localhost:3030/diablocode/grafosEmpleabilidad/ontology/empleabilidad#`; it is a vocabulary IRI, not the Fuseki service endpoint. Repository ontology v1.1.0 has been synchronized to Fuseki.
- **Current dataset snapshot:** The default graph contains 1,450 triples, including 20 candidates, 5 offers, 5 recommendations, and 5 explanations. Full SHACL validation of the graph passed with zero validation results. The five offers all have `emp:fechaFin` `2025-12-31` and none has `emp:estadoOferta`. The user confirmed `fechaFin` means expiry, so all five are stale as of 2026-11-06 and must be excluded from current recommendations. The user accepts an empty current-offers state until the graph is updated with active listings.
- **Local Fuseki data model:** The instance data is in the dataset's default graph, with no named graphs reported. Profiles will be written into that same graph as source job data, so all profile updates must be narrowly scoped and preserve source offer triples.
- **Local application origins:** React at `http://localhost:5173` and Spring Boot at `http://localhost:8080`. The local Google web OAuth client is confirmed to authorize the React origin. Use the supported GIS credential flow rather than building a separate app login screen; keep the secret-bearing client JSON untracked.
- **Profile persistence:** Store profile RDF in the same RDF graph as source opportunity data and associate each candidate with an opaque stable identifier. Enforce profile ownership in the application and constrain update operations to the authenticated user's profile triples; do not encode personal information in resource identifiers.
- **Graph/domain mapping:** Use the ontology as the initial semantic contract, then validate the exact graph structure, identifiers, optionality, and populated properties against instance data. Create typed mappings for supported classes and relationships rather than treating every ontology class as a first-release feature.
- **Configuration:** Keep Google OAuth credentials and Fuseki connection details outside source code. Provide environment-specific configuration for local development and document required settings.

These are architectural requirements and boundaries, not a finalized deployment design. The ontology namespace `https://localhost:3030/diablocode/grafosEmpleabilidad/ontology/empleabilidad#` is confirmed as the stable vocabulary IRI; it remains distinct from the Fuseki service URL. Production hosting and remote database access are outside the initial local-development setup.

### Ontology vocabulary currently available

The checked-in ontology defines the following relevant vocabulary:

- **Candidate/profile:** `emp:Candidato`, connected to `emp:Educacion`, `emp:ExperienciaLaboral`, `emp:Competencia`, `emp:Habilidad`, `emp:HabilidadSuave`, `emp:Lenguaje`, `emp:RankingDeCompetencia`, and `emp:TrayectoriaLaboral`.
- **Optional profile information:** `emp:PerfilSocioemocional` and `emp:FactorSocioemocional` represent contextualized evaluations, not protected attributes. `emp:RedSocial` means a known professional person or group, not a digital social network; it can link to `emp:RecursoSocial` through `emp:facilitaRecursoSocial`. Both sections are available from the first release but optional.
- **Job offers:** `emp:OfertaLaboral` linked to `emp:Empleador` by `emp:publicadoPor` and to `emp:RequerimientoDeCompetencia` by `emp:requiereCompetencia`. A requirement identifies a competency and may state an expected level and whether it is mandatory. The ontology also defines offer location, modality, and publication status.
- **Recommendations:** `emp:Recomendacion` and `emp:Explicacion` can link a recommended offer and candidate to evidence. The ontology includes recommendation type concepts and fields for model version and recommendation validity.
- **Controlled levels:** Competency rankings have a defined 1–5 concept scheme; language levels use MCER concepts from A1 through C2. These are separate scales and must not be conflated.

The checked-in TTL and RDF/XML files describe vocabulary; Fuseki also contains instance records. Queries found five offers with names, identifiers, employer links, one competency requirement each, locations, and modalities. Four requirements are mandatory and expected levels are 3–4. All five offers end `2025-12-31`; the user confirmed that `fechaFin` is their expiry date, so none should appear as current. Five stored recommendations are dated in 2025 and use model version `ejemplo-sintetico-1.0`; they should be treated as seed/example recommendations, not automatically fresh personalized results. Candidate records include core professional information and competence rankings on the defined 1–5 scale; 17 have soft skills, 5 have language data, 4 have practical skills, and 1 has a professional network. No socioemotional profile/factor instances were found. No named graphs were returned by the dataset query. The live graph contains ontology v1.1.0 and passed SHACL validation with zero violations; the newly synchronized contact properties should be populated by the profile workflow.

The ontology and queried offer records do not establish accepted `estadoOferta` values; the offers have no status property and all share the expired `fechaFin` date above. Candidate location/preferences, compensation, and application URLs are also not defined by the current vocabulary/records. Confirm whether these properties exist in later data or require a separately reviewed ontology extension. The newly synchronized contact properties do not yet have profile values. The shared-graph write strategy still requires application-level ownership checks and regression tests before profile writes are safe.

## 9. Non-functional requirements

- **Security:** Use established OAuth/OIDC libraries and secure session/token handling; protect state-changing requests; enforce per-user authorization; validate inputs; never expose credentials, access tokens, or sensitive configuration in logs or client code.
- **Privacy:** Collect only profile information needed for the product. Explain the intended use of profile data for recommendations. Define retention and account-deletion behavior before production release.
- **Reliability:** A Fuseki timeout, unavailable dataset, malformed result, or SPARQL error must produce a clear application-level error and must not be presented as a successful empty result.
- **Performance:** Paginate or limit result sets where appropriate. Set a configurable timeout for Fuseki requests. Establish response-time targets after dataset size and local environment are known.
- **Maintainability:** Separate API, authentication, profile, graph-query, and ranking responsibilities. Keep graph vocabulary and mapping decisions centralized and documented.
- **Accessibility:** Target WCAG 2.1 AA practices for core flows, subject to validation during implementation.
- **Compatibility:** Support current versions of major desktop and mobile browsers.

## 10. Out of scope for the first release

- Creating, editing, or administrating job offers through the application.
- Uploading or editing graph files through the UI.
- Extending or replacing the ontology before validating the existing vocabulary against representative instance data.
- Guaranteeing job eligibility, hiring outcomes, or completeness/accuracy of source data.
- Automated applications, messaging recruiters, or importing a user's information from external services.
- Production hosting and multi-environment infrastructure beyond configuration needed to keep local development usable.

## 11. Acceptance criteria for the initial product

- A user can sign in with Google, sign out, and return to the application without creating an application password.
- An authenticated user can create and update their own profile; requests for another user's profile are denied.
- Profile form fields and their validation reflect an agreed schema derived from the supplied data and matching needs.
- The backend can query the configured local Fuseki dataset using SPARQL and map returned data using the agreed graph model.
- Current `OfertaLaboral` results are filtered and ranked according to the agreed, testable rules. With the current graph, all five offers are expired and the application shows the agreed empty state; use current offer fixtures or refreshed graph data to verify non-empty ranking behavior.
- Each ranked result includes an explanation derived from the data used in its match.
- No-results, incomplete-profile, and Fuseki-unavailable states are distinguishable to the user.
- The React interface works on mobile and desktop for the sign-in, profile, and recommendation flows.
- Credentials and connection secrets are provided through local configuration and are not committed to the repository.

## 12. Dependencies, risks, and remaining decisions

- **Freshness and status semantics (resolved):** `fechaFin` is the offer expiry date. Exclude offers ending before today and those whose known `estadoOferta` is `cerrada`, `expirada`, or `cubierta`; other known status values may remain eligible. If `fechaFin` is missing, require a known non-excluded status; if both status and end date are missing, exclude as indeterminate. The five current offers are expired; the user accepts no current results until Fuseki is updated.
- **Graph validation (completed):** The live default graph was validated against its SHACL shapes; it conforms with zero validation results.
- **Profile data requirements:** Candidate name and email are required profile fields and phone is optional. Use existing `emp:nombre`, new `emp:correoContacto`, and optional `emp:telefonoContacto` from repository ontology v1.1.0. Synchronize Fuseki before profile storage. Socioemotional and professional-network sections are collected as optional profile data from the first release and may affect recommendations when provided; expose their use clearly, explain resulting matches, and apply privacy/minimization safeguards.
- **Shared-graph write safety:** Configure write access so application profile updates cannot replace or remove source opportunity triples. Enforce per-user profile ownership at the application layer because profiles and job data share one graph.
- **Ranking method:** Agree on signals, weights or ordering, tie handling, and explanations once instance data and profile fields are known. Respect the 1–5 competency scale and MCER levels; do not invent conversion between unrelated scales. Any initial heuristic should be presented as a relevance aid, not an objective eligibility score.
- **Google setup (confirmed):** The local web OAuth client authorizes React at `http://localhost:5173`. Use the public client ID for Google Identity Services; keep the client secret out of the browser and source control.
- **Local Fuseki setup:** Confirmed Fuseki 6.2.0, base URL `http://localhost:3030`, dataset `/empleabilidad`, default-graph data, and query/update service endpoints. Decide how developers without the local dataset will run tests.
- **Remaining data/product decisions:** Confirm ranking weights and tie handling during ranking design; decide profile retention/deletion behavior before production; revisit candidate preferences, compensation, application URLs, and status values if future graph data introduces them. The user will refresh expired offers later; this does not block initial integration.

## 13. Suggested delivery sequence

1. **Completed in Layer 0:** Inspect the existing ontology and representative instance data plus Fuseki details; validate vocabulary conformance.
2. Agree on profile fields, current-opportunity rules, ranking signals, and profile storage.
3. Implement local configuration and a backend Fuseki/SPARQL integration with representative tests.
4. Implement Google sign-in, profile ownership, and profile management.
5. Implement ranking, match explanations, and recommendation APIs.
6. Build React sign-in, profile, and recommendation experiences; verify loading, empty, and error states.
7. Validate end-to-end flows with representative graph data and review privacy, accessibility, and security before release.
