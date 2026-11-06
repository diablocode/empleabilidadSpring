# Empleabilidad Implementation Plan

**Status:** Draft — Layer 0 is complete; implementation decisions remain  
**Related document:** [PRD.md](PRD.md)  
**Plan date:** 2026-11-06

## 1. Implementation objective

Build the Empleabilidad application in independently verifiable layers: a React web client, a Spring Boot API, Google authentication, Fuseki-backed RDF data access, user profiles, and explainable job recommendations. Integrate the layers incrementally so each has tests and a runnable local setup before moving to the next.

The existing repository is a minimal Spring Boot/Gradle starter using Java 21 and Spring Boot 4.1.2. It currently has no React client, security, persistence, or Fuseki integration. The repository's Empleabilidad ontology is version 1.1.0 in both `src/main/resources/ontology/empleabilidad.ttl` and `src/main/resources/ontology/empleabilidad.rdf`, and has been synchronized to Fuseki.

## 2. Decisions and working assumptions

### Confirmed

- Frontend: React.
- Backend: Spring Boot.
- Job opportunity graph store: local Apache Jena Fuseki, queried with SPARQL.
- User profiles: store as RDF in Fuseki, per the user's preference.
- Profile writes to Fuseki: allowed in the exact same RDF graph as source job data; application updates must be constrained to owned profile triples and preserve source offer data.
- Ontology vocabulary: use version 1.1.0, synchronized in the repository and live Fuseki dataset. Full SHACL validation of the live graph passes with zero violations. Version 1.1.0 adds candidate contact properties.
- Stable ontology namespace: `https://localhost:3030/diablocode/grafosEmpleabilidad/ontology/empleabilidad#` (confirmed by the user); this vocabulary IRI is distinct from the Fuseki service endpoint.
- Local Fuseki: `http://localhost:3030`, dataset `/empleabilidad`, Fuseki 6.2.0; data currently resides in the default graph.
- Google Cloud developer account to use for OAuth setup: `robles.gomez.juan@gmail.com` (provided by the user). A local web OAuth client JSON file is present; its client ID and client secret were not displayed. The file is ignored by Git and must stay untracked. The user confirmed the React origin `http://localhost:5173` is authorized for this client.
- Local development origins: React at `http://localhost:5173` and Spring Boot at `http://localhost:8080`.
- Users sign in with Google and receive profile-based ranked `OfertaLaboral` recommendations.
- Candidate profiles include name and email as required application fields and phone as an optional field, in addition to professional information.
- **Completed:** Added `emp:correoContacto` and optional `emp:telefonoContacto` as candidate datatype properties to both ontology serializations. `emp:nombre` remains the candidate name property. The repository ontology is now 1.1.0 with `owl:priorVersion` 1.0.0.
- Include optional socioemotional and professional-network data in the first release, and allow these signals to influence recommendations when provided. Their use must be visible and explainable; neither section is required to complete the profile.
- Initial ranking priority: competency matches and mandatory competency levels first, with other graph-supported signals secondary.
- Show an application destination only if a valid link is present in the graph; otherwise show offer details without a substitute link.

### Implementation constraints

- The React client will use Google's official Google Identity Services sign-in button/client. Google hosts/controls the credential authentication experience; the app will not provide username/password, password reset, or a separate registration/login form.
- The GIS credential returned to the frontend will be sent to a backend endpoint for server-side validation. On successful validation, Spring Boot will establish the application's session; Google credentials and raw ID tokens will not be persisted in the application.
- React will call only the Spring Boot API; Fuseki will remain server-side.
- Local development may run the React dev server and Spring Boot separately, with a development proxy for API calls. Production packaging/hosting is not part of this initial plan.
- RDF profiles will be stored in the same graph as source job data, linked to an opaque identifier derived from Google's stable subject claim. Never put personal information into resource identifiers; enforce ownership in the application and never let profile updates replace/delete source offer triples.
- The ontology namespace is a vocabulary IRI, not the Fuseki HTTP endpoint. Preserve it when creating persistent resource IRIs.

## 3. Layered delivery plan

### Layer 0 — Confirm inputs and local environment

**Status:** Complete for environment, data, and access prerequisites. Fuseki 6.2.0 and dataset `/empleabilidad` are inspected; ontology v1.1.0 is synchronized; SHACL validation passes; Google client origin is confirmed; and the reversible SPARQL Update probe was cleaned up and verified absent. Application-level profile ownership and update-safety tests remain implementation work in later layers.

**Purpose:** Resolve prerequisites before schema-dependent implementation.

**Completed checks**

- Inspected both ontology serializations, confirmed the stable namespace, and synchronized ontology v1.1.0 to Fuseki. Verified the live version and the new contact properties' candidate domain and string range.
- Confirmed Fuseki 6.2.0 at `http://localhost:3030`, dataset `/empleabilidad`, query/update endpoints, default-graph storage, and no named graphs. The graph contains 1,450 triples: 20 candidates, 5 offers, 5 recommendations, and 5 explanations.
- Inspected the instance data. All five offers end `2025-12-31` and have no `emp:estadoOferta`; the user confirmed `emp:fechaFin` is expiry and accepted an empty current-offers state until the graph is updated. The stored 2025 recommendations are seed examples, not automatically fresh results.
- Reviewed candidate relationships and scales: competency rankings use the defined 1–5 scale; MCER language levels are separate. Optional socioemotional and professional-network information may influence recommendations but is not required. `emp:RedSocial` means a known professional person or group, not an electronic social network.
- Validated the live graph against 12 SHACL NodeShapes and 41 property shapes; it conforms with zero validation results.
- With the user's permission, inserted a temporary unique triple through SPARQL Update, then deleted it and verified it was absent. This verifies endpoint write capability only, not application-level authorization or safe update scoping.
- Confirmed the local Google web OAuth client's authorized React origin is `http://localhost:5173`. The credential file remains Git-ignored; its ID and secret were not displayed. Credential validation and session implementation remain in Layer 3.
- Confirmed the offer-current rule: exclude past `emp:fechaFin` and statuses `cerrada`, `expirada`, or `cubierta`; if the end date is missing, require a known non-excluded status; if date and status are both missing, exclude as indeterminate.

**Later-layer requirements and follow-up**

- Constrain profile writes to the authenticated user's candidate resources; add tests proving they cannot modify source offers or another user's data.
- Prioritize competency matches and mandatory levels, keep optional profile fields optional, and explain any secondary signals used.
- Candidate location/preferences, compensation, and application URLs are not defined by the current vocabulary/records; revisit if future graph data requires them. Show application links only when valid destinations are present in the graph.
- React and Spring Boot will run as separate local processes. Add current-offer fixtures or refresh the graph when non-empty ranking examples are needed.

**Exit criteria**

- **Met:** Repository ontology namespace and 1.1.0 contact-property changes, shared default-graph profile storage, local service/app origins, Fuseki version/dataset, instance class/property coverage, ranking priority, optional ranking inputs, and application-link behavior are confirmed.
- **Met for initial integration:** The app may start against the current graph and show the empty-current-offers state until the user updates it. Current offers must exclude the five expired records.
- **Met for Layer 0:** Full SHACL validation passes. Add application-level tests proving profile updates cannot modify source offers or other users' data in Layers 3–4.
- **Met for setup:** Google web OAuth client exists and its React origin is confirmed. Use its client ID through local environment configuration; do not commit the credential JSON or expose its client secret.

**Dependency:** Layer 0 environment and graph checks are complete. Profile-write scoping and ownership tests remain required in Layers 3–4; Fuseki dataset `/empleabilidad` is running and ontology v1.1.0 is synchronized.

### Layer 1 — Establish project structure and developer configuration

**Status:** Complete. See [README.md](README.md) for local setup and run instructions.

**Purpose:** Create a reproducible local development foundation without embedding secrets.

**Delivered:** Added a Vite/React TypeScript client with a `/api` development proxy and a harmless backend health view; environment-variable defaults and ignored local examples for Fuseki, GIS, service ports, and the frontend origin; startup validation for local URL/dataset settings; independent API and Fuseki health checks; and Windows PowerShell setup/run instructions.

**Work**

- Keep the existing Gradle/Spring Boot application as the backend.
- Add a separately managed React client with a documented development command and API proxy/base URL configuration.
- Establish environment-specific configuration for Fuseki endpoint and dataset, Google Identity Services client ID, frontend origin, and backend port. Keep any backend-only secrets out of the frontend.
- Add an example configuration file containing placeholders only; ensure real credentials and local data files are excluded from version control.
- Add health/readiness endpoints that distinguish application availability from Fuseki connectivity where appropriate.
- Document commands to start each component and the required local services.

**Verification**

- Backend builds and starts with non-secret defaults suitable for local testing.
- React client starts and reaches a harmless backend health endpoint through its configured API path.
- Missing required settings produce actionable startup/configuration errors rather than silent fallbacks.

### Layer 2 — Model and query graph data

**Status:** Complete. See [README.md](README.md) for graph access behavior and optional live Fuseki integration test instructions.

**Delivered:** Added Apache Jena ARQ, startup loading/version and required-term validation of the bundled ontology, typed RDF mappings and centralized vocabulary for offers, employers, competency requirements, candidates/ranking scales, recommendation concepts, and explanations. Added safe bounded offer listing/detail queries, a five-second Fuseki timeout, explicit query/mapping failures, and current-offer classification that excludes expired/closed/unknown-status/indeterminate records. Unit tests cover mappings and freshness; a live Fuseki integration test is opt-in.

**Purpose:** Create the graph integration boundary from the supplied data.

**Work**

- **Completed:** Added Apache Jena ARQ and a graph-access component with a five-second timeout.
- **Completed:** Load and validate ontology v1.1.0 at startup; map candidates, offers, employers, requirements, rankings, languages, controlled recommendation concepts, and explanations into typed models.
- **Completed:** Preserve class distinctions and competency scale IRIs; validate scores against the known 1–5 scale and map MCER/recommendation concepts by IRI and notation.
- **Completed:** Add parameterized detail lookup and bounded offer pages (maximum 50); centralize vocabulary and SPARQL templates.
- **Completed:** Keep absent fields optional, classify unknown statuses explicitly, and apply the agreed expiration/closed/indeterminate policy. The live graph contains no offer status values; new eligible statuses must be added only after their meaning is confirmed.
- **Completed:** Surface Fuseki, timeout, malformed RDF, and graph mapping errors explicitly, separately from valid empty results.

**Verification**

- **Passed:** Unit tests cover RDF mappings, absent and malformed properties, safe page limits, and offer freshness.
- **Passed:** Local HTTP fixture tests distinguish valid empty RDF responses from Fuseki and malformed-response errors.
- **Passed:** Opt-in integration test queries the configured Fuseki dataset, checks pagination/lookup, and verifies read-only connectivity.

**Dependency:** Layer 0 graph inspection.

### Layer 3 — Google authentication and authorization

**Purpose:** Establish secure authenticated API access before personal data is exposed.

**Work**

- Integrate Google's official Google Identity Services button/client in React; Google owns the credential selection and authentication experience.
- Add a backend credential-validation endpoint that verifies the Google credential's signature, issuer, audience, and expiration using trusted Google verification mechanisms.
- After successful validation, establish the application's own secure session and define cookie and CSRF behavior for the React-to-backend request pattern.
- Add current-user and sign-out flows; do not build username/password, password-reset, or app-specific sign-up/login screens.
- Associate application identity with Google's stable subject claim; use email only as profile/display data where appropriate.
- Protect profile and recommendation APIs and enforce ownership at the backend.
- Return consistent unauthenticated/forbidden responses for API calls.

**Verification**

- Security tests cover unauthenticated access, successful authenticated access, sign-out, and ownership checks.
- Manual local test confirms the Google-provided sign-in button, credential validation, and application session flow.
- No Google password, raw ID token, or backend secret is persisted or logged. The GIS client ID may be exposed as public frontend configuration.

**Dependency:** Layer 1 configuration of the confirmed GIS client ID and authorized React origin; backend credential validation remains to be implemented.

### Layer 4 — User profile storage and API

**Purpose:** Let each authenticated user create and maintain the RDF profile used for recommendations.

**Work**

- Define profile fields from ontology-supported concepts: education, work experience, competencies, skills, soft skills, language levels, competence rankings, and optional socioemotional and professional-network data. Add name/contact RDF predicates through a reviewed ontology extension or approved mapping; do not silently reuse generic `emp:nombre` or other broad predicates without agreeing on semantics.
- Keep socioemotional and professional-network fields optional. If values are provided and affect ranking, ensure the reason is visible, the user can understand the signal, and missing values do not penalize profile completeness.
- Design how profiles are represented in the shared Fuseki graph, including stable opaque user linkage, permitted profile predicates, and safe updates. Keep user-owned candidate resources distinct from source offer/employer resources; constrain updates so a candidate can only change their own profile and source data cannot be altered through profile operations.
- Implement read/create/update operations scoped to the authenticated user's stable identity.
- Validate submitted fields and keep application-controlled profile predicates separate from job-source data.
- Define behavior for absent profiles, incomplete profiles, and graph write errors.

**Verification**

- Tests prove one user cannot read, overwrite, or delete another user's profile.
- Integration tests verify profile round-tripping through Fuseki.
- Invalid values and Fuseki write failures are reported explicitly.

**Dependencies:** Layers 2 and 3; profile vocabulary and storage design from Layer 0.

### Layer 5 — Recommendation and ranking service

**Purpose:** Produce predictable, explainable recommendations from profile and opportunity data.

**Work**

- Implement the agreed initial priority: competency matches and mandatory requirement levels first, other supported signals secondary. Candidate competence rankings may be compared with `emp:nivelEsperado` only when both values use the same defined scale; MCER language levels remain a separate signal.
- Allow supplied socioemotional and professional-network data to influence recommendations only through documented, explainable signals; do not infer protected traits.
- Implement ranking as a separate backend service with deterministic behavior, tie handling, and explicit handling of missing profile/opportunity attributes.
- Include match reasons derived from the same signals used to rank each opportunity. Where recommendations are persisted as RDF, use `emp:Recomendacion`/`emp:Explicacion`, supported SKOS recommendation types, `emp:utilizaEvidencia`, `emp:versionModelo`, and `emp:vigenteHasta` as applicable.
- Apply the agreed current-opportunity rule and bounded result size.
- Keep recommendations framed as relevance guidance, not an eligibility or hiring guarantee.

**Verification**

- Unit tests cover ranking order, ties, missing values, current listing filtering, and reason generation.
- Fixture-based tests prove that the explanations correspond to the actual ranking inputs.
- API tests cover incomplete profiles, no matches, and Fuseki failure separately.

**Dependencies:** Layers 2 and 4; ranking decisions and graph-supported fields from Layer 0.

### Layer 6 — Spring Boot API contract

**Purpose:** Provide a stable client-facing API over authentication, profiles, and recommendations.

**Work**

- Define and document endpoints for current user, profile read/update, opportunity detail/listing, and personalized recommendations.
- Specify request/response types, validation errors, pagination/limits, authentication requirements, and error formats.
- Keep Fuseki/RDF implementation details out of the public API responses.
- Add API-level integration tests for happy paths and expected error states.

**Verification**

- Endpoint contract tests verify response shape and authorization.
- API documentation matches the implemented behavior.

**Dependencies:** Layers 2–5.

### Layer 7 — React application

**Purpose:** Deliver the job-seeker experience against the API contract.

**Work**

- Add React routing and shared API/authentication handling.
- Build sign-in and signed-in navigation states.
- Build profile creation/editing based on the approved profile schema, with validation and clear completion guidance.
- Build ranked recommendation cards/list, match-reason presentation, and available opportunity details or application links.
- Implement loading, empty, incomplete-profile, unauthenticated, and service-error states.
- Support keyboard navigation, accessible labels, responsive layouts, and appropriate color contrast.

**Verification**

- Component tests cover key flows and state rendering.
- End-to-end tests cover the Google sign-in handoff (using a testable auth setup, without automating Google's UI), profile save, and recommendation display.
- Manually review the core flows at mobile and desktop viewport sizes.

**Dependency:** Layer 6 API contract; profile UI waits for approved fields.

### Layer 8 — End-to-end hardening and release readiness

**Purpose:** Verify integrated behavior and reduce risks before the initial release.

**Work**

- Run the full local stack against representative graph data.
- Verify privacy boundaries, Google Identity Services configuration, credential validation, CSRF/session behavior, input validation, and error handling.
- Test accessibility of sign-in, profile, and recommendation flows.
- Add operational guidance for starting Fuseki, loading the dataset, configuring secrets, and diagnosing connection failures.
- Review data retention/account deletion expectations before exposing the service beyond local development.

**Exit criteria**

- PRD acceptance criteria pass end-to-end.
- No credentials or private graph data are committed.
- Setup instructions allow another developer to run the application with the required local services and their own OAuth configuration.

## 4. Cross-layer quality requirements

- Add tests with each layer rather than postponing tests until the end.
- Keep network access to Fuseki and Google behind backend components; the React application must not contain Fuseki credentials or Google client secrets.
- Use stable typed API models and explicit error responses.
- Separate test fixtures from user-supplied graph data and avoid logging profile contents or tokens.
- Treat Fuseki unavailability as a service failure, not as “no recommendations.”
- Keep the RDF vocabulary and mapping decisions centralized so graph changes can be handled without rewriting the UI.

## 5. Dependencies and implementation gates

| Gate | Required input | Unblocks |
|---|---|---|
| G1: Graph understanding | Met: ontology v1.1.0, instance records, SHACL conformance, and offer-freshness semantics confirmed | Domain classes, SPARQL, current-offer rule, profile-to-graph matching |
| G2: Fuseki connectivity | Met: endpoint/dataset and update capability confirmed; profile-update scoping and ownership tests remain | Integration tests and profile persistence |
| G3: Authentication setup | Met: local GIS client and authorized React origin confirmed; backend credential validation remains | End-to-end sign-in |
| G4: Profile and ranking agreement | Profile fields and approved matching policy | Profile UI, validation, ranking implementation |
| G5: API contract | Agreed endpoint and response shapes | Frontend integration |

Non-dependent setup, project structure, and test scaffolding can proceed before all gates are satisfied. Avoid inventing final graph fields or match weights to make a prototype appear complete.

## 6. Remaining questions

Layer 0 prerequisites are complete. Resolve these product/data questions during the relevant implementation layers:

1. Agree on ranking weights or ordering, tie handling, and explanation details during Layer 5.
2. Decide profile retention and deletion behavior before production release.
3. If future graph files introduce candidate preferences, compensation, application URLs, or status values, review their semantics and whether the ontology needs an extension.

## 7. Recommended implementation order

Layers 0–2 are complete: the live graph conforms to SHACL, ontology v1.1.0 is synchronized, current offers are known to be expired, and the typed Fuseki boundary is implemented. Active offer data is not required to proceed; the app can initially show the agreed empty state. Next implement authentication and profile ownership (Layers 3–4), ranking and API contract (Layers 5–6), and React flows (Layer 7). Complete integrated verification and local setup documentation in Layer 8.
